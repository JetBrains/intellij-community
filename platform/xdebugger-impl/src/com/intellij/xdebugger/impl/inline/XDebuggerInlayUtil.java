// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.xdebugger.impl.inline;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.editor.Inlay;
import com.intellij.openapi.editor.InlayProperties;
import com.intellij.openapi.editor.event.EditorFactoryEvent;
import com.intellij.openapi.editor.event.EditorFactoryListener;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.TextEditor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.util.ui.EDT;
import com.intellij.xdebugger.*;
import com.intellij.xdebugger.impl.frame.XVariablesView;
import com.intellij.xdebugger.impl.ui.DebuggerUIUtil;
import com.intellij.xdebugger.impl.ui.tree.XDebuggerTreeListener;
import com.intellij.xdebugger.impl.ui.tree.nodes.RestorableStateNode;
import com.intellij.xdebugger.impl.ui.tree.nodes.XValueNodeImpl;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

@Service
public final class XDebuggerInlayUtil {
  public static final String INLINE_HINTS_DELIMETER = ":";
  @NotNull private final Project myProject;

  public static XDebuggerInlayUtil getInstance(Project project) {
    return project.getService(XDebuggerInlayUtil.class);
  }

  XDebuggerInlayUtil(Project project) {
    myProject = project;
    project.getMessageBus().connect().subscribe(XDebuggerManager.TOPIC, new XDebuggerManagerListener() {
      @Override
      public void processStopped(@NotNull XDebugProcess debugProcess) {
        XVariablesView.InlineVariablesInfo.set(debugProcess.getSession(), null);
      }

      @Override
      public void currentSessionChanged(@Nullable XDebugSession previousSession,
                                        @Nullable XDebugSession currentSession) {
        ApplicationManager.getApplication().invokeLater(() -> {
          if (previousSession != null && !previousSession.isStopped()) {
            XVariablesView.InlineVariablesInfo info = XVariablesView.InlineVariablesInfo.get(previousSession);
            if (info != null) {
              info.setInlays(clearInlaysInt(project));
            }
          }
          if (currentSession != null) {
            XVariablesView.InlineVariablesInfo info = XVariablesView.InlineVariablesInfo.get(currentSession);
            if (info != null) {
              info.getInlays().forEach(renderer -> createInlayInt(currentSession, renderer));
            }
          }
          DebuggerUIUtil.repaintCurrentEditor(project); // to update inline debugger data
        }, project.getDisposed());
      }
    });
    EditorFactory.getInstance().addEditorFactoryListener(new EditorFactoryListener() {
      @Override
      public void editorReleased(@NotNull EditorFactoryEvent event) {
        clearInlaysInEditor(event.getEditor());
      }
    }, project);
  }

  public boolean createLineEndInlay(@NotNull XValueNodeImpl valueNode,
                                    @NotNull XDebugSession session,
                                    @NotNull XSourcePosition position) {
    if (valueNode.getValuePresentation() != null) {
      ApplicationManager.getApplication().invokeLater(() -> {
        createInlayInt(session, new InlineDebugRenderer(valueNode, position, session));
      }, session.getProject().getDisposed());
      return true;
    }
    return false;
  }

  private static void createInlayInt(@NotNull XDebugSession session, InlineDebugRenderer renderer) {
    EDT.assertIsEdt();
    XSourcePosition position = renderer.getPosition();
    FileEditor editor = FileEditorManager.getInstance(session.getProject()).getSelectedEditor(position.getFile());
    if (editor instanceof TextEditor) {
      Editor e = ((TextEditor)editor).getEditor();
      int offset = e.getDocument().getLineEndOffset(position.getLine());
      Inlay<InlineDebugRenderer> inlay = e.getInlayModel().addAfterLineEndElement(offset,
                                                                                  new InlayProperties()
                                                                                    .disableSoftWrapping(true)
                                                                                    .priority(renderer.isCustomNode() ? 0 : -1),
                                                                                  renderer);
      if (inlay == null) {
        return;
      }
      XValueNodeImpl valueNode = renderer.getValueNode();
      valueNode.getTree().addTreeListener(new XDebuggerTreeListener() {
        @Override
        public void nodeLoaded(@NotNull RestorableStateNode node, @NotNull String name) {
          if (node == valueNode) {
            renderer.updatePresentation();
            inlay.update();
          }
        }
      }, inlay);

      if (renderer.isCustomNode()) {
        ((InlineWatchNodeImpl)valueNode).inlayCreated(inlay);
      }
    }
  }

  public void clearInlays() {
    ApplicationManager.getApplication().invokeLater(() -> clearInlaysInt(myProject), myProject.getDisposed());
  }

  private static List<Inlay> clearInlaysInEditor(@NotNull Editor editor) {
    EDT.assertIsEdt();
    List<? extends Inlay> inlays =
      editor.getInlayModel().getAfterLineEndElementsInRange(0, editor.getDocument().getTextLength(), InlineDebugRenderer.class);
    inlays.forEach(Disposer::dispose);
    //noinspection unchecked
    return (List<Inlay>)inlays;
  }

  private static List<InlineDebugRenderer> clearInlaysInt(@NotNull Project project) {
    return StreamEx.of(FileEditorManager.getInstance(project).getAllEditors())
      .select(TextEditor.class)
      .flatCollection(textEditor -> clearInlaysInEditor(textEditor.getEditor()))
      .map(Inlay::getRenderer)
      .select(InlineDebugRenderer.class)
      .toList();
  }
}
