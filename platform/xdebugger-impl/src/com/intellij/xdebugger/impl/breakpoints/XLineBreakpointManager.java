/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

import com.intellij.ide.startup.StartupManagerEx;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.Result;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.editor.event.*;
import com.intellij.openapi.editor.markup.MarkupEditorFilterFactory;
import com.intellij.openapi.editor.markup.RangeHighlighter;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.startup.StartupManager;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.Disposable;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.util.containers.BidirectionalMap;
import com.intellij.util.ui.update.MergingUpdateQueue;
import com.intellij.util.ui.update.Update;
import com.intellij.xdebugger.XDebuggerManager;
import com.intellij.xdebugger.XDebuggerUtil;
import com.intellij.xdebugger.breakpoints.XBreakpoint;
import gnu.trove.TIntHashSet;
import org.jetbrains.annotations.NotNull;

import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.Collection;
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
      DocumentAdapter documentListener = new MyDocumentListener();
      EditorMouseAdapter editorMouseListener = new MyEditorMouseListener();

      EditorEventMulticaster editorEventMulticaster = EditorFactory.getInstance().getEventMulticaster();
      editorEventMulticaster.addDocumentListener(documentListener,project);
      editorEventMulticaster.addEditorMouseListener(editorMouseListener, project);

      final MyDependentBreakpointListener myDependentBreakpointListener = new MyDependentBreakpointListener();
      myDependentBreakpointManager.addListener(myDependentBreakpointListener);
      Disposer.register(project, new Disposable() {
        public void dispose() {
          myDependentBreakpointManager.removeListener(myDependentBreakpointListener);
        }
      });
    }
    myBreakpointsUpdateQueue = new MergingUpdateQueue("XLine breakpoints", 300, true, null, project);
  }

  public void updateBreakpointsUI() {
    if (myProject.isDefault()) return;

    Runnable runnable = new Runnable() {
      public void run() {
        for (XLineBreakpointImpl breakpoint : myBreakpoints.keySet()) {
          breakpoint.updateUI();
        }
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

  private void updateBreakpoints(final Document document) {
    Collection<XLineBreakpointImpl> breakpoints = myBreakpoints.getKeysByValue(document);
    if (breakpoints == null) return;

    TIntHashSet lines = new TIntHashSet();
    final List<XLineBreakpointImpl> toRemove = new ArrayList<XLineBreakpointImpl>();
    for (XLineBreakpointImpl breakpoint : breakpoints) {
      breakpoint.updatePosition();
      if (!breakpoint.isValid() || !lines.add(breakpoint.getLine())) {
        toRemove.add(breakpoint);
      }
    }

    new WriteAction() {
      protected void run(final Result result) {
        for (XLineBreakpointImpl breakpoint : toRemove) {
          XDebuggerManager.getInstance(myProject).getBreakpointManager().removeBreakpoint(breakpoint);
        }
      }
    }.execute();
  }

  public void breakpointChanged(final XLineBreakpointImpl breakpoint) {
    ApplicationManager.getApplication().assertIsDispatchThread();
    breakpoint.updateUI();
  }

  public void queueBreakpointUpdate(final XBreakpoint<?> slave) {
    if (slave instanceof XLineBreakpointImpl<?>) {
      queueBreakpointUpdate((XLineBreakpointImpl<?>)slave);
    }
  }

  public void queueBreakpointUpdate(@NotNull final XLineBreakpointImpl<?> breakpoint) {
    myBreakpointsUpdateQueue.queue(new Update(breakpoint) {
      public void run() {
        breakpoint.updateUI();
      }
    });
  }

  public void queueAllBreakpointsUpdate() {
    myBreakpointsUpdateQueue.queue(new Update("all breakpoints") {
      public void run() {
        for (XLineBreakpointImpl breakpoint : myBreakpoints.keySet()) {
          breakpoint.updateUI();
        }
      }
    });
  }

  private class MyDocumentListener extends DocumentAdapter {
    public void documentChanged(final DocumentEvent e) {
      final Document document = e.getDocument();
      Collection<XLineBreakpointImpl> breakpoints = myBreakpoints.getKeysByValue(document);
      if (breakpoints != null && !breakpoints.isEmpty()) {
        myBreakpointsUpdateQueue.queue(new Update(document) {
          public void run() {
            updateBreakpoints(document);
          }
        });
      }
    }
  }

  private class MyEditorMouseListener extends EditorMouseAdapter {
    public void mouseClicked(final EditorMouseEvent e) {
      final Editor editor = e.getEditor();
      final MouseEvent mouseEvent = e.getMouseEvent();
      if (mouseEvent.isPopupTrigger() ||
          mouseEvent.getButton() != MouseEvent.BUTTON1 ||
          MarkupEditorFilterFactory.createIsDiffFilter().avaliableIn(editor) ||
          e.getArea() != EditorMouseEventArea.LINE_MARKERS_AREA) {
        return;
      }

      PsiDocumentManager.getInstance(myProject).commitAndRunReadAction(new Runnable() {
        public void run() {
          final int line = editor.xyToLogicalPosition(mouseEvent.getPoint()).line;
          final Document document = editor.getDocument();
          final VirtualFile file = FileDocumentManager.getInstance().getFile(document);
          if (line >= 0 && line < document.getLineCount() && file != null) {
            ApplicationManager.getApplication().invokeLater(new Runnable() {
              public void run() {
                if (!myProject.isDisposed() && myProject.isInitialized() && file.isValid()) {
                  XDebuggerUtil.getInstance().toggleLineBreakpoint(myProject, file, line);
                }
              }
            });
          }
        }
      });
    }
  }

  private class MyDependentBreakpointListener implements XDependentBreakpointListener {
    public void dependencySet(final XBreakpoint<?> slave, final XBreakpoint<?> master) {
      queueBreakpointUpdate(slave);
    }

    public void dependencyCleared(final XBreakpoint<?> breakpoint) {
      queueBreakpointUpdate(breakpoint);
    }
  }
}
