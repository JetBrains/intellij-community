// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.xdebugger.impl.breakpoints;

import com.intellij.AppTopics;
import com.intellij.execution.impl.ConsoleViewUtil;
import com.intellij.ide.DataManager;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.ex.ActionUtil;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.diff.impl.DiffUtil;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.editor.colors.EditorColorsListener;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.editor.event.*;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.editor.ex.util.EditorUtil;
import com.intellij.openapi.fileEditor.*;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.startup.StartupManager;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileEvent;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.openapi.vfs.VirtualFileUrlChangeAdapter;
import com.intellij.openapi.vfs.impl.BulkVirtualFileListenerAdapter;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.ui.ExperimentalUI;
import com.intellij.util.SmartList;
import com.intellij.util.containers.BidirectionalMap;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.messages.MessageBusConnection;
import com.intellij.util.ui.update.MergingUpdateQueue;
import com.intellij.util.ui.update.Update;
import com.intellij.xdebugger.XDebuggerManager;
import com.intellij.xdebugger.breakpoints.SuspendPolicy;
import com.intellij.xdebugger.breakpoints.XBreakpoint;
import com.intellij.xdebugger.breakpoints.XBreakpointManager;
import com.intellij.xdebugger.breakpoints.XLineBreakpoint;
import com.intellij.xdebugger.impl.XSourcePositionImpl;
import com.intellij.xdebugger.impl.ui.DebuggerUIUtil;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.stream.Stream;

public final class XLineBreakpointManager {
  private final BidirectionalMap<XLineBreakpointImpl, String> myBreakpoints = new BidirectionalMap<>();
  private final MergingUpdateQueue myBreakpointsUpdateQueue;
  private final Project myProject;

  public XLineBreakpointManager(@NotNull Project project) {
    myProject = project;

    MessageBusConnection busConnection = project.getMessageBus().connect();

    if (!myProject.isDefault()) {
      EditorEventMulticaster editorEventMulticaster = EditorFactory.getInstance().getEventMulticaster();
      editorEventMulticaster.addDocumentListener(new MyDocumentListener(), project);
      editorEventMulticaster.addEditorMouseListener(new MyEditorMouseListener(), project);
      editorEventMulticaster.addEditorMouseMotionListener(new MyEditorMouseMotionListener(), project);

      busConnection.subscribe(XDependentBreakpointListener.TOPIC, new MyDependentBreakpointListener());
      busConnection.subscribe(VirtualFileManager.VFS_CHANGES, new BulkVirtualFileListenerAdapter(new VirtualFileUrlChangeAdapter() {
        @Override
        protected void fileUrlChanged(String oldUrl, String newUrl) {
          breakpoints().forEach(breakpoint -> {
            String url = breakpoint.getFileUrl();
            if (FileUtil.startsWith(url, oldUrl)) {
              breakpoint.setFileUrl(newUrl + url.substring(oldUrl.length()));
            }
          });
        }

        @Override
        public void fileDeleted(@NotNull VirtualFileEvent event) {
          List<XLineBreakpointImpl> breakpoints = myBreakpoints.getKeysByValue(event.getFile().getUrl());
          removeBreakpoints(breakpoints != null ? new ArrayList<>(breakpoints) : null); // safe copy
        }
      }));
    }
    myBreakpointsUpdateQueue = new MergingUpdateQueue("XLine breakpoints", 300, true, null, project);

    // Update breakpoints colors if global color schema was changed
    busConnection.subscribe(EditorColorsManager.TOPIC, new MyEditorColorsListener());
    busConnection.subscribe(AppTopics.FILE_DOCUMENT_SYNC, new FileDocumentManagerListener() {
      @Override
      public void fileContentLoaded(@NotNull VirtualFile file, @NotNull Document document) {
        List<XLineBreakpointImpl> breakpoints = myBreakpoints.getKeysByValue(file.getUrl());
        if (breakpoints != null) {
          breakpoints.stream().filter(b -> b.getHighlighter() == null).forEach(XLineBreakpointManager.this::queueBreakpointUpdate);
        }
      }
    });
  }

  void updateBreakpointsUI() {
    if (ApplicationManager.getApplication().isUnitTestMode()) {
      return;
    }

    StartupManager.getInstance(myProject).runAfterOpened(() -> {
      for (XLineBreakpointImpl<?> breakpoint : myBreakpoints.keySet()) {
        ApplicationManager.getApplication().invokeLater(() -> {
          breakpoint.updateUI();
        }, ModalityState.NON_MODAL, myProject.getDisposed());
      }
    });
  }

  public void registerBreakpoint(XLineBreakpointImpl breakpoint, final boolean initUI) {
    if (initUI) {
      breakpoint.updateUI();
    }
    myBreakpoints.put(breakpoint, breakpoint.getFileUrl());
  }

  public void unregisterBreakpoint(final XLineBreakpointImpl breakpoint) {
    myBreakpoints.remove(breakpoint);
  }

  @NotNull
  public Collection<XLineBreakpointImpl> getDocumentBreakpoints(Document document) {
    VirtualFile file = FileDocumentManager.getInstance().getFile(document);
    if (file != null) {
      Collection<XLineBreakpointImpl> breakpoints = myBreakpoints.getKeysByValue(file.getUrl());
      if (breakpoints != null) {
        return new ArrayList<>(breakpoints);
      }
    }
    return Collections.emptyList();
  }

  private Stream<XLineBreakpointImpl> breakpoints() {
    return myBreakpoints.keySet().stream();
  }

  private void updateBreakpoints(@NotNull Document document) {
    Collection<XLineBreakpointImpl> breakpoints = getDocumentBreakpoints(document);

    if (breakpoints.isEmpty() || ApplicationManager.getApplication().isUnitTestMode()) {
      return;
    }

    IntSet lines = new IntOpenHashSet();
    List<XLineBreakpoint> toRemove = new SmartList<>();
    for (XLineBreakpointImpl breakpoint : breakpoints) {
      breakpoint.updatePosition();
      if (!breakpoint.isValid() || !lines.add(breakpoint.getLine())) {
        toRemove.add(breakpoint);
      }
    }

    removeBreakpoints(toRemove);
  }

  private void removeBreakpoints(@Nullable final List<? extends XLineBreakpoint> toRemove) {
    if (ContainerUtil.isEmpty(toRemove)) {
      return;
    }

    XBreakpointManager manager = XDebuggerManager.getInstance(myProject).getBreakpointManager();
    WriteAction.run(() -> toRemove.forEach(manager::removeBreakpoint));
  }

  public void breakpointChanged(XLineBreakpointImpl breakpoint) {
    if (ApplicationManager.getApplication().isDispatchThread()) {
      breakpoint.updateUI();
    }
    else {
      queueBreakpointUpdate(breakpoint);
    }
  }

  public void queueBreakpointUpdate(final XBreakpoint<?> slave) {
    queueBreakpointUpdate(slave, null);
  }

   public void queueBreakpointUpdate(final XBreakpoint<?> slave, @Nullable Runnable callOnUpdate) {
    if (slave instanceof XLineBreakpointImpl<?>) {
      queueBreakpointUpdate((XLineBreakpointImpl<?>)slave, callOnUpdate);
    }
  }

  public void queueBreakpointUpdate(@NotNull final XLineBreakpointImpl<?> breakpoint) {
    queueBreakpointUpdate(breakpoint, null);
  }

  public void queueBreakpointUpdate(@NotNull final XLineBreakpointImpl<?> breakpoint, @Nullable Runnable callOnUpdate) {
    myBreakpointsUpdateQueue.queue(new Update(breakpoint) {
      @Override
      public void run() {
        breakpoint.updateUI();
        if (callOnUpdate != null) {
          callOnUpdate.run();
        }
      }
    });
  }

  public void queueAllBreakpointsUpdate() {
    myBreakpointsUpdateQueue.queue(new Update("all breakpoints") {
      @Override
      public void run() {
        breakpoints().forEach(XLineBreakpointImpl::updateUI);
      }
    });
  }

  private class MyDocumentListener implements DocumentListener {
    @Override
    public void documentChanged(@NotNull final DocumentEvent e) {
      final Document document = e.getDocument();
      Collection<XLineBreakpointImpl> breakpoints = getDocumentBreakpoints(document);
      if (!breakpoints.isEmpty()) {
        myBreakpointsUpdateQueue.queue(new Update(document) {
          @Override
          public void run() {
            updateBreakpoints(document);
          }
        });
      }
    }
  }

  private boolean myDragDetected = false;

  private class MyEditorMouseMotionListener implements EditorMouseMotionListener {
    @Override
    public void mouseDragged(@NotNull EditorMouseEvent e) {
      myDragDetected = true;
    }
  }

  private class MyEditorMouseListener implements EditorMouseListener {
    @Override
    public void mousePressed(@NotNull EditorMouseEvent e) {
      myDragDetected = false;
    }

    @Override
    public void mouseClicked(@NotNull final EditorMouseEvent e) {
      final Editor editor = e.getEditor();
      final MouseEvent mouseEvent = e.getMouseEvent();
      if (mouseEvent.isPopupTrigger()
          || mouseEvent.isMetaDown() || mouseEvent.isControlDown()
          || mouseEvent.getButton() != MouseEvent.BUTTON1
          || DiffUtil.isDiffEditor(editor)
          || !isInsideGutter(e, editor)
          || ConsoleViewUtil.isConsoleViewEditor(editor)
          || !isFromMyProject(editor)
          || (editor.getSelectionModel().hasSelection() && myDragDetected)
        ) {
        return;
      }

      final Document document = editor.getDocument();
      PsiDocumentManager.getInstance(myProject).commitDocument(document);
      final int line = EditorUtil.yToLogicalLineNoCustomRenderers(editor, mouseEvent.getY());
      final VirtualFile file = FileDocumentManager.getInstance().getFile(document);
      if (line >= 0 && line < document.getLineCount() && file != null) {
        AnAction action = ActionManager.getInstance().getAction(IdeActions.ACTION_TOGGLE_LINE_BREAKPOINT);
        if (action == null) throw new AssertionError("'" + IdeActions.ACTION_TOGGLE_LINE_BREAKPOINT + "' action not found");
        DataContext dataContext = DataManager.getInstance().getDataContext(mouseEvent.getComponent());
        AnActionEvent event = AnActionEvent.createFromAnAction(action, mouseEvent, ActionPlaces.EDITOR_GUTTER, dataContext);
        ActionUtil.performDumbAwareWithCallbacks(action, event, () ->
          XBreakpointUtil.toggleLineBreakpoint(myProject,
                                               XSourcePositionImpl.create(file, line),
                                               editor,
                                               mouseEvent.isAltDown(),
                                               false,
                                               !mouseEvent.isShiftDown() && !Registry.is("debugger.click.disable.breakpoints"))
            .onSuccess(breakpoint -> {
              if (!mouseEvent.isAltDown() && mouseEvent.isShiftDown() && breakpoint != null) {
                breakpoint.setSuspendPolicy(SuspendPolicy.NONE);
                String selection = editor.getSelectionModel().getSelectedText();
                if (selection != null) {
                  breakpoint.setLogExpression(selection);
                }
                else {
                  breakpoint.setLogMessage(true);
                }
                // edit breakpoint
                DebuggerUIUtil
                  .showXBreakpointEditorBalloon(myProject, mouseEvent.getPoint(), ((EditorEx)editor).getGutterComponentEx(),
                                                false, breakpoint);
              }
            }));
      }
    }

    private boolean isInsideGutter(EditorMouseEvent e, Editor editor) {
      if (ExperimentalUI.isNewUI() && e.getArea() == EditorMouseEventArea.LINE_NUMBERS_AREA) {
        return true;
      }
      if (e.getArea() != EditorMouseEventArea.LINE_MARKERS_AREA && e.getArea() != EditorMouseEventArea.FOLDING_OUTLINE_AREA) {
        return false;
      }
      return e.getMouseEvent().getX() <= ((EditorEx)editor).getGutterComponentEx().getWhitespaceSeparatorOffset();
    }
  }

  private boolean isFromMyProject(@NotNull Editor editor) {
    if (myProject == editor.getProject()) {
      return true;
    }

    for (FileEditor fileEditor : FileEditorManager.getInstance(myProject).getAllEditors()) {
      if (fileEditor instanceof TextEditor && ((TextEditor)fileEditor).getEditor().equals(editor)) {
        return true;
      }
    }
    return false;
  }

  private class MyDependentBreakpointListener implements XDependentBreakpointListener {
    @Override
    public void dependencySet(@NotNull final XBreakpoint<?> slave, @NotNull final XBreakpoint<?> master) {
      queueBreakpointUpdate(slave);
    }

    @Override
    public void dependencyCleared(final XBreakpoint<?> breakpoint) {
      queueBreakpointUpdate(breakpoint);
    }
  }

  private class MyEditorColorsListener implements EditorColorsListener {
    @Override
    public void globalSchemeChange(EditorColorsScheme scheme) {
      updateBreakpointsUI();
    }
  }
}
