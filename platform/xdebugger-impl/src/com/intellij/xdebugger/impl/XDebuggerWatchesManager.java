// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger.impl;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.openapi.editor.event.DocumentListener;
import com.intellij.openapi.editor.event.EditorEventMulticaster;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileEditor.FileDocumentManagerListener;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.util.concurrency.annotations.RequiresEdt;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.update.MergingUpdateQueue;
import com.intellij.util.ui.update.Update;
import com.intellij.xdebugger.*;
import com.intellij.xdebugger.impl.breakpoints.XExpressionState;
import com.intellij.xdebugger.impl.inline.InlineWatch;
import com.intellij.xdebugger.impl.inline.InlineWatchInplaceEditor;
import com.intellij.xdebugger.impl.inline.XInlineWatchesView;
import kotlinx.coroutines.CoroutineScope;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@ApiStatus.Internal
public final class XDebuggerWatchesManager {
  private final Map<String, List<XExpression>> watches = new ConcurrentHashMap<>();
  private final Map<String, Set<InlineWatch>> inlineWatches = new ConcurrentHashMap<>();
  private final MergingUpdateQueue myInlinesUpdateQueue;
  private final Project myProject;

  public XDebuggerWatchesManager(@NotNull Project project, @NotNull CoroutineScope coroutineScope) {
    myProject = project;
    EditorEventMulticaster editorEventMulticaster = EditorFactory.getInstance().getEventMulticaster();
    editorEventMulticaster.addDocumentListener(new MyDocumentListener(), project);
    myProject.getMessageBus().connect().subscribe(FileDocumentManagerListener.TOPIC, new FileDocumentManagerListener() {
      @Override
      public void fileContentLoaded(@NotNull VirtualFile file, @NotNull Document document) {
        getDocumentInlines(document).forEach(InlineWatch::setMarker);
      }
    });
    myInlinesUpdateQueue = MergingUpdateQueue.Companion.edtMergingUpdateQueue("XInlineWatches", 300, coroutineScope);
  }

  public @NotNull List<XExpression> getWatches(String confName) {
    return ContainerUtil.notNullize(watches.get(confName));
  }

  public void setWatches(@NotNull String configurationName, @NotNull List<XExpression> expressions) {
    if (expressions.isEmpty()) {
      watches.remove(configurationName);
    }
    else {
      watches.put(configurationName, expressions);
    }
  }

  public List<InlineWatch> getInlineWatches() {
    return inlineWatches.values().stream().flatMap(l -> l.stream()).collect(Collectors.toList());
  }

  @ApiStatus.Internal
  public @NotNull WatchesManagerState saveState(@NotNull WatchesManagerState state) {
    List<ConfigurationState> expressions = state.getExpressions();
    expressions.clear();
    watches.forEach((key, value) -> expressions.add(new ConfigurationState(key, value)));
    List<InlineWatchState> inlineExpressionStates = state.getInlineExpressionStates();
    inlineExpressionStates.clear();
    inlineWatches.values().stream()
      .flatMap(l -> l.stream())
      .forEach((value) -> {
        inlineExpressionStates.add(new InlineWatchState(value.getExpression(), value.getLine(), value.getPosition().getFile().getUrl()));
      });
    return state;
  }

  public void clearContext() {
    watches.clear();
    inlineWatches.clear();
  }

  @ApiStatus.Internal
  public void loadState(@NotNull WatchesManagerState state) {
    clearContext();

    for (ConfigurationState configurationState : state.getExpressions()) {
      List<WatchState> expressionStates = configurationState.getExpressionStates();
      if (!ContainerUtil.isEmpty(expressionStates)) {
        watches.put(configurationState.getName(), ContainerUtil.mapNotNull(expressionStates, XExpressionState::toXExpression));
      }
    }

    VirtualFileManager fileManager = VirtualFileManager.getInstance();
    XDebuggerUtil debuggerUtil = XDebuggerUtil.getInstance();
    for (InlineWatchState inlineWatchState : state.getInlineExpressionStates()) {
      if (inlineWatchState.getFileUrl() == null || inlineWatchState.getWatchState() == null) continue;

      VirtualFile file = fileManager.findFileByUrl(inlineWatchState.getFileUrl());
      XSourcePosition position = debuggerUtil.createPosition(file, inlineWatchState.getLine());
      XExpression expression = inlineWatchState.getWatchState().toXExpression();
      if (position == null || expression == null) continue;

      InlineWatch watch = new InlineWatch(expression, position);
      inlineWatches.computeIfAbsent(inlineWatchState.getFileUrl(), (k) -> new HashSet<>()).add(watch);
    }

    // set markers in the background
    ApplicationManager.getApplication().executeOnPooledThread(() -> {
      for (InlineWatch i : ContainerUtil.flatten(inlineWatches.values())) {
        ReadAction.nonBlocking(() -> {
          if (!i.setMarker()) {
            inlineWatches.get(i.getPosition().getFile().getUrl()).remove(i);
          }
        }).executeSynchronously();
      }
    });
  }

  public void showInplaceEditor(@NotNull XSourcePosition presentationPosition,
                                @NotNull Editor mainEditor,
                                @NotNull XDebugSession session,
                                @Nullable XExpression expression) {
    InlineWatchInplaceEditor inplaceEditor = new InlineWatchInplaceEditor(presentationPosition, session, mainEditor, expression);
    inplaceEditor.show();
  }

  public void inlineWatchesRemoved(List<InlineWatch> removed, XInlineWatchesView watchesView) {
    inlineWatches.values().forEach(set -> removed.forEach(set::remove));
    getWatchesViews().filter(v -> v != watchesView).forEach(view -> view.removeInlineWatches(removed));
  }

  private class MyDocumentListener implements DocumentListener {
    @Override
    public void documentChanged(@NotNull final DocumentEvent e) {
      final Document document = e.getDocument();
      Collection<InlineWatch> inlines = getDocumentInlines(document);
      if (!inlines.isEmpty()) {
        myInlinesUpdateQueue.queue(new Update(document) {
          @Override
          public void run() {
            updateInlines(document);
          }
        });
      }
    }
  }

  @RequiresEdt
  public void addInlineWatchExpression(@NotNull XExpression expression, int index, XSourcePosition position, boolean navigateToWatchNode) {
    InlineWatch watch = new InlineWatch(expression, position);
    watch.setMarker();
    String fileUrl = position.getFile().getUrl();
    inlineWatches.computeIfAbsent(fileUrl, (k) -> new HashSet<>()).add(watch);

    getWatchesViews().forEach(view -> view.addInlineWatchExpression(watch, index, navigateToWatchNode));
  }


  private void updateInlines(@NotNull Document document) {
    @NotNull Collection<InlineWatch> inlines = getDocumentInlines(document);
    if (inlines.isEmpty()) return;

    Set<InlineWatch> toRemove = new HashSet<>();
    for (InlineWatch inlineWatch : inlines) {
      if (!inlineWatch.updatePosition()) {
        toRemove.add(inlineWatch);
      }
    }
    removeInlines(toRemove);
  }

  private void removeInlines(Collection<InlineWatch> remove) {
    for (InlineWatch watch : remove) {
      inlineWatches.get(watch.getPosition().getFile().getUrl()).remove(watch);
    }

    getWatchesViews().forEach(view -> view.removeInlineWatches(remove));
  }

  private Stream<XInlineWatchesView> getWatchesViews() {
    return Arrays.stream(XDebuggerManager.getInstance(myProject).getDebugSessions())
      .map(s -> ((XDebugSessionImpl)s).getSessionTab())
      .filter(t -> t != null && t.getWatchesView() instanceof XInlineWatchesView)
      .map(t -> (XInlineWatchesView)t.getWatchesView());
  }

  @NotNull
  public Collection<InlineWatch> getDocumentInlines(Document document) {
    VirtualFile file = FileDocumentManager.getInstance().getFile(document);
    if (file != null) {
      Set<InlineWatch> inlineWatches = this.inlineWatches.get(file.getUrl());
      if (inlineWatches != null) {
        return new ArrayList<>(inlineWatches);
      }
    }
    return Collections.emptyList();
  }
}
