/*
 * Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */
package com.intellij.xdebugger.impl.breakpoints;

import com.intellij.execution.impl.ConsoleViewUtil;
import com.intellij.openapi.actionSystem.IdeActions;
import com.intellij.openapi.actionSystem.ex.ActionManagerEx;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.editor.colors.EditorColorsListener;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.editor.event.*;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.editor.ex.util.EditorUtil;
import com.intellij.openapi.editor.markup.MarkupEditorFilterFactory;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.TextEditor;
import com.intellij.openapi.project.DumbAwareRunnable;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.startup.StartupManager;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileEvent;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.openapi.vfs.VirtualFileUrlChangeAdapter;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.util.SmartList;
import com.intellij.util.containers.BidirectionalMap;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.update.MergingUpdateQueue;
import com.intellij.util.ui.update.Update;
import com.intellij.xdebugger.XDebuggerManager;
import com.intellij.xdebugger.breakpoints.SuspendPolicy;
import com.intellij.xdebugger.breakpoints.XBreakpoint;
import com.intellij.xdebugger.breakpoints.XBreakpointManager;
import com.intellij.xdebugger.breakpoints.XLineBreakpoint;
import com.intellij.xdebugger.impl.XSourcePositionImpl;
import com.intellij.xdebugger.impl.ui.DebuggerUIUtil;
import gnu.trove.TIntHashSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.stream.Stream;

/**
 * @author nik
 */
public class XLineBreakpointManager {
  private final BidirectionalMap<XLineBreakpointImpl, String> myBreakpoints = new BidirectionalMap<>();
  private final MergingUpdateQueue myBreakpointsUpdateQueue;
  private final Project myProject;
  private final XDependentBreakpointManager myDependentBreakpointManager;

  public XLineBreakpointManager(@NotNull Project project, final XDependentBreakpointManager dependentBreakpointManager) {
    myProject = project;
    myDependentBreakpointManager = dependentBreakpointManager;

    if (!myProject.isDefault()) {
      EditorEventMulticaster editorEventMulticaster = EditorFactory.getInstance().getEventMulticaster();
      editorEventMulticaster.addDocumentListener(new MyDocumentListener(), project);
      editorEventMulticaster.addEditorMouseListener(new MyEditorMouseListener(), project);
      editorEventMulticaster.addEditorMouseMotionListener(new MyEditorMouseMotionListener(), project);

      final MyDependentBreakpointListener myDependentBreakpointListener = new MyDependentBreakpointListener();
      myDependentBreakpointManager.addListener(myDependentBreakpointListener);
      Disposer.register(project, () -> myDependentBreakpointManager.removeListener(myDependentBreakpointListener));
      VirtualFileManager.getInstance().addVirtualFileListener(new VirtualFileUrlChangeAdapter() {
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
      }, project);
    }
    myBreakpointsUpdateQueue = new MergingUpdateQueue("XLine breakpoints", 300, true, null, project);

    // Update breakpoints colors if global color schema was changed
    project.getMessageBus().connect().subscribe(EditorColorsManager.TOPIC, new MyEditorColorsListener());
  }

  void updateBreakpointsUI() {
    StartupManager.getInstance(myProject).runWhenProjectIsInitialized(
      (DumbAwareRunnable)() -> breakpoints().forEach(XLineBreakpointImpl::updateUI));
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
        return breakpoints;
      }
    }
    return Collections.emptyList();
  }

  private Stream<XLineBreakpointImpl> breakpoints() {
    return myBreakpoints.keySet().stream();
  }

  private void updateBreakpoints(@NotNull Document document) {
    Collection<XLineBreakpointImpl> breakpoints = getDocumentBreakpoints(document);

    if (breakpoints.isEmpty()) {
      return;
    }

    TIntHashSet lines = new TIntHashSet();
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

  public void breakpointChanged(final XLineBreakpointImpl breakpoint) {
    if (ApplicationManager.getApplication().isDispatchThread()) {
      breakpoint.updateUI();
    }
    else {
      queueBreakpointUpdate(breakpoint);
    }
  }

  public void queueBreakpointUpdate(final XBreakpoint<?> slave) {
    if (slave instanceof XLineBreakpointImpl<?>) {
      queueBreakpointUpdate((XLineBreakpointImpl<?>)slave);
    }
  }

  public void queueBreakpointUpdate(@NotNull final XLineBreakpointImpl<?> breakpoint) {
    myBreakpointsUpdateQueue.queue(new Update(breakpoint) {
      @Override
      public void run() {
        breakpoint.updateUI();
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
    public void documentChanged(final DocumentEvent e) {
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

  private class MyEditorMouseMotionListener extends EditorMouseMotionAdapter {
    @Override
    public void mouseDragged(EditorMouseEvent e) {
      myDragDetected = true;
    }
  }

  private class MyEditorMouseListener extends EditorMouseAdapter {
    @Override
    public void mousePressed(EditorMouseEvent e) {
      myDragDetected = false;
    }

    @Override
    public void mouseClicked(final EditorMouseEvent e) {
      final Editor editor = e.getEditor();
      final MouseEvent mouseEvent = e.getMouseEvent();
      if (mouseEvent.isPopupTrigger()
          || mouseEvent.isMetaDown() || mouseEvent.isControlDown()
          || mouseEvent.getButton() != MouseEvent.BUTTON1
          || MarkupEditorFilterFactory.createIsDiffFilter().avaliableIn(editor)
          || !isInsideGutter(e, editor)
          || ConsoleViewUtil.isConsoleViewEditor(editor)
          || !isFromMyProject(editor)
          || (editor.getSelectionModel().hasSelection() && myDragDetected)
        ) {
        return;
      }

      PsiDocumentManager.getInstance(myProject).commitAllDocuments();
      final int line = EditorUtil.yPositionToLogicalLine(editor, mouseEvent);
      final Document document = editor.getDocument();
      final VirtualFile file = FileDocumentManager.getInstance().getFile(document);
      if (line >= 0 && line < document.getLineCount() && file != null) {
        ActionManagerEx.getInstanceEx().fireBeforeActionPerformed(IdeActions.ACTION_TOGGLE_LINE_BREAKPOINT, e.getMouseEvent());

        XBreakpointUtil.toggleLineBreakpoint(myProject,
                                             XSourcePositionImpl.create(file, line),
                                             editor,
                                             mouseEvent.isAltDown(),
                                             false,
                                             !mouseEvent.isShiftDown() && !Registry.is("debugger.click.disable.breakpoints"))
          .done(breakpoint -> {
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
          });
      }
    }

    private boolean isInsideGutter(EditorMouseEvent e, Editor editor) {
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
    public void dependencySet(final XBreakpoint<?> slave, final XBreakpoint<?> master) {
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
