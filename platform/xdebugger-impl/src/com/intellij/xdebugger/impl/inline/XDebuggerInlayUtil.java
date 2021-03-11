// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.xdebugger.impl.inline;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.Inlay;
import com.intellij.openapi.editor.impl.InlayModelImpl;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.TextEditor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.xdebugger.XDebugSession;
import com.intellij.xdebugger.XSourcePosition;
import com.intellij.xdebugger.impl.ui.tree.XDebuggerTree;
import com.intellij.xdebugger.impl.ui.tree.XDebuggerTreeListener;
import com.intellij.xdebugger.impl.ui.tree.nodes.RestorableStateNode;
import com.intellij.xdebugger.impl.ui.tree.nodes.XValueNodeImpl;
import org.jetbrains.annotations.NotNull;

public final class XDebuggerInlayUtil {
  public static final String INLINE_HINTS_DELIMETER = ":";

  public static boolean createLineEndInlay(XValueNodeImpl valueNode,
                                           @NotNull XDebugSession session,
                                           @NotNull VirtualFile file,
                                           @NotNull XSourcePosition position,
                                           Document document) {
    if (valueNode.getValuePresentation() != null) {
      ApplicationManager.getApplication().invokeLater(() -> {
        int offset = document.getLineEndOffset(position.getLine());
        Project project = session.getProject();
        FileEditor editor = FileEditorManager.getInstance(project).getSelectedEditor(file);
        if (editor instanceof TextEditor) {
          Editor e = ((TextEditor)editor).getEditor();
          boolean customNode = valueNode instanceof InlineWatchNodeImpl;
          InlineDebugRenderer renderer = new InlineDebugRenderer(valueNode, position, session, e);
          Inlay<InlineDebugRenderer> inlay = ((InlayModelImpl)e.getInlayModel()).addAfterLineEndDebuggerHint(offset, customNode, renderer);
          XDebuggerTreeListener loadListener = new XDebuggerTreeListener() {
            @Override
            public void nodeLoaded(@NotNull RestorableStateNode node, @NotNull String name) {
              if (node == valueNode) {
                renderer.updatePresentation();
                inlay.update();
              }
            }
          };
          XDebuggerTree tree = valueNode.getTree();
          tree.addTreeListener(loadListener);
          Disposer.register(inlay, () -> tree.removeTreeListener(loadListener));

          if (customNode) {
            ((InlineWatchNodeImpl)valueNode).inlayCreated(inlay);
          }
        }
      }, session.getProject().getDisposed());
      return true;
    }
    return false;
  }

  public static void clearInlays(@NotNull Project project) {
    ApplicationManager.getApplication().invokeLater(() -> {
      FileEditor[] editors = FileEditorManager.getInstance(project).getAllEditors();
      for (FileEditor editor : editors) {
        if (editor instanceof TextEditor) {
          Editor e = ((TextEditor)editor).getEditor();
          e.getInlayModel().getAfterLineEndElementsInRange(0, e.getDocument().getTextLength(), InlineDebugRenderer.class).forEach(Disposer::dispose);
        }
      }
    }, project.getDisposed());
  }
}
