/*
 * Copyright 2000-2016 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.xdebugger.impl.breakpoints;

import com.intellij.execution.impl.ConsoleViewUtil;
import com.intellij.ide.startup.StartupManagerEx;
import com.intellij.openapi.actionSystem.IdeActions;
import com.intellij.openapi.actionSystem.ex.ActionManagerEx;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.editor.colors.EditorColorsAdapter;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.editor.event.*;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.editor.ex.util.EditorUtil;
import com.intellij.openapi.editor.markup.MarkupEditorFilterFactory;
import com.intellij.openapi.editor.markup.RangeHighlighter;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.TextEditor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.startup.StartupManager;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileEvent;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.openapi.vfs.VirtualFileUrlChangeAdapter;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.util.SmartList;
import com.intellij.util.containers.BidirectionalMap;
import com.intellij.util.ui.update.MergingUpdateQueue;
import com.intellij.util.ui.update.Update;
import com.intellij.xdebugger.XDebuggerManager;
import com.intellij.xdebugger.breakpoints.SuspendPolicy;
import com.intellij.xdebugger.breakpoints.XBreakpoint;
import com.intellij.xdebugger.impl.XSourcePositionImpl;
import com.intellij.xdebugger.impl.ui.DebuggerUIUtil;
import gnu.trove.TIntHashSet;
import org.jetbrains.annotations.NotNull;

import java.awt.event.MouseEvent;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * @author nik
 */
public class XLineBreakpointManager {
  private final BidirectionalMap<XLineBreakpointImpl, Document> myBreakpoints = new BidirectionalMap<XLineBreakpointImpl, Document>();
  private final MergingUpdateQueue myBreakpointsUpdateQueue;
  private final Project myProject;
  private final XDependentBreakpointManager myDependentBreakpointManager;
  private final StartupManagerEx myStartupManager;

  public XLineBreakpointManager(Project project, final XDependentBreakpointManager dependentBreakpointManager, final StartupManager startupManager) {
    myProject = project;
    myDependentBreakpointManager = dependentBreakpointManager;
    myStartupManager = (StartupManagerEx)startupManager;

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
          for (XLineBreakpointImpl breakpoint : myBreakpoints.keySet()) {
            final String url = breakpoint.getFileUrl();
            if (FileUtil.startsWith(url, oldUrl)) {
              breakpoint.setFileUrl(newUrl + url.substring(oldUrl.length()));
            }
          }
        }

        @Override
        public void fileDeleted(@NotNull VirtualFileEvent event) {
          List<XBreakpoint<?>> toRemove = new SmartList<XBreakpoint<?>>();
          for (XLineBreakpointImpl breakpoint : myBreakpoints.keySet()) {
            if (breakpoint.getFileUrl().equals(event.getFile().getUrl())) {
              toRemove.add(breakpoint);
            }
          }
          removeBreakpoints(toRemove);
        }
      }, project);
    }
    myBreakpointsUpdateQueue = new MergingUpdateQueue("XLine breakpoints", 300, true, null, project);

    // Update breakpoints colors if global color schema was changed
    final EditorColorsManager colorsManager = EditorColorsManager.getInstance();
    if (colorsManager != null) { // in some debugger tests EditorColorsManager component isn't loaded
      colorsManager.addEditorColorsListener(new MyEditorColorsListener(), project);
    }
  }

  public void updateBreakpointsUI() {
    if (myProject.isDefault()) return;

    Runnable runnable = () -> {
      for (XLineBreakpointImpl breakpoint : myBreakpoints.keySet()) {
        breakpoint.updateUI();
      }
    };

    if (ApplicationManager.getApplication().isUnitTestMode() || myStartupManager.startupActivityPassed()) {
      runnable.run();
    }
    else {
      myStartupManager.registerPostStartupActivity(runnable);
    }
  }

  public void registerBreakpoint(XLineBreakpointImpl breakpoint, final boolean initUI) {
    if (initUI) {
      breakpoint.updateUI();
    }
    Document document = breakpoint.getDocument();
    if (document != null) {
      myBreakpoints.put(breakpoint, document);
    }
  }

  public void unregisterBreakpoint(final XLineBreakpointImpl breakpoint) {
    RangeHighlighter highlighter = breakpoint.getHighlighter();
    if (highlighter != null) {
      myBreakpoints.remove(breakpoint);
    }
  }

  @NotNull
  public Collection<XLineBreakpointImpl> getDocumentBreakpoints(Document document) {
    Collection<XLineBreakpointImpl> breakpoints = myBreakpoints.getKeysByValue(document);
    if (breakpoints == null) {
      breakpoints = Collections.emptyList();
    }
    return breakpoints;
  }

  private void updateBreakpoints(@NotNull Document document) {
    Collection<XLineBreakpointImpl> breakpoints = myBreakpoints.getKeysByValue(document);
    if (breakpoints == null) {
      return;
    }

    TIntHashSet lines = new TIntHashSet();
    List<XBreakpoint<?>> toRemove = new SmartList<XBreakpoint<?>>();
    for (XLineBreakpointImpl breakpoint : breakpoints) {
      breakpoint.updatePosition();
      if (!breakpoint.isValid() || !lines.add(breakpoint.getLine())) {
        toRemove.add(breakpoint);
      }
    }

    removeBreakpoints(toRemove);
  }

  private void removeBreakpoints(final List<? extends XBreakpoint<?>> toRemove) {
    if (toRemove.isEmpty()) {
      return;
    }

    ApplicationManager.getApplication().runWriteAction(() -> {
      for (XBreakpoint<?> breakpoint : toRemove) {
        XDebuggerManager.getInstance(myProject).getBreakpointManager().removeBreakpoint(breakpoint);
      }
    });
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
        for (XLineBreakpointImpl breakpoint : myBreakpoints.keySet()) {
          breakpoint.updateUI();
        }
      }
    });
  }

  private class MyDocumentListener extends DocumentAdapter {
    @Override
    public void documentChanged(final DocumentEvent e) {
      final Document document = e.getDocument();
      Collection<XLineBreakpointImpl> breakpoints = myBreakpoints.getKeysByValue(document);
      if (breakpoints != null && !breakpoints.isEmpty()) {
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

      PsiDocumentManager.getInstance(myProject).commitAndRunReadAction(() -> {
        final int line = EditorUtil.yPositionToLogicalLine(editor, mouseEvent);
        final Document document = editor.getDocument();
        final VirtualFile file = FileDocumentManager.getInstance().getFile(document);
        if (line >= 0 && line < document.getLineCount() && file != null) {
          ApplicationManager.getApplication().invokeLater(() -> {
            if (!myProject.isDisposed() && myProject.isInitialized() && file.isValid()) {
              ActionManagerEx.getInstanceEx().fireBeforeActionPerformed(IdeActions.ACTION_TOGGLE_LINE_BREAKPOINT, e.getMouseEvent());

              XBreakpointUtil
                .toggleLineBreakpoint(myProject, XSourcePositionImpl.create(file, line), editor, mouseEvent.isAltDown(), false)
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
          });
        }
      });
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

  private class MyEditorColorsListener extends EditorColorsAdapter {
    @Override
    public void globalSchemeChange(EditorColorsScheme scheme) {
      updateBreakpointsUI();
    }
  }
}
