/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.intellij.openapi.vcs.changes.patch;

import com.intellij.diff.DiffContentFactory;
import com.intellij.diff.FrameDiffTool;
import com.intellij.diff.contents.DocumentContent;
import com.intellij.diff.merge.*;
import com.intellij.diff.requests.SimpleDiffRequest;
import com.intellij.diff.tools.simple.SimpleDiffViewer;
import com.intellij.diff.util.DiffUtil;
import com.intellij.openapi.command.undo.DocumentReference;
import com.intellij.openapi.command.undo.DocumentReferenceManager;
import com.intellij.openapi.command.undo.UndoManager;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.BooleanGetter;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.EditorNotificationPanel;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.event.ActionEvent;

public class ApplyPatchMergeTool implements MergeTool {
  @NotNull
  @Override
  public MergeViewer createComponent(@NotNull MergeContext context, @NotNull MergeRequest request) {
    return new MyViewer(context, (ApplyPatchMergeRequest)request);
  }

  @Override
  public boolean canShow(@NotNull MergeContext context, @NotNull MergeRequest request) {
    return request instanceof ApplyPatchMergeRequest;
  }

  private static class MyViewer implements MergeViewer {
    @NotNull private final MergeContext myMergeContext;
    @NotNull private final ApplyPatchMergeRequest myMergeRequest;

    @NotNull private final SimpleDiffViewer myViewer;

    public MyViewer(@NotNull MergeContext context, @NotNull ApplyPatchMergeRequest request) {
      myMergeContext = context;
      myMergeRequest = request;

      MergeUtil.ProxyDiffContext diffContext = new MergeUtil.ProxyDiffContext(myMergeContext);

      VirtualFile file = FileDocumentManager.getInstance().getFile(myMergeRequest.getDocument());
      final DiffContentFactory contentFactory = DiffContentFactory.getInstance();
      final DocumentContent localContent = contentFactory.create(myMergeRequest.getLocalContent(), file);
      final DocumentContent mergedContent = contentFactory.create(myMergeRequest.getProject(), myMergeRequest.getDocument());

      SimpleDiffRequest diffRequest = new SimpleDiffRequest(myMergeRequest.getTitle(), localContent, mergedContent,
                                                            myMergeRequest.getLocalTitle(), myMergeRequest.getPatchedTitle());
      DiffUtil.addNotification(new DiffIsApproximateNotification(), diffRequest);

      myViewer = new SimpleDiffViewer(diffContext, diffRequest);
    }

    @NotNull
    @Override
    public JComponent getComponent() {
      return myViewer.getComponent();
    }

    @Nullable
    @Override
    public JComponent getPreferredFocusedComponent() {
      return myViewer.getPreferredFocusedComponent();
    }

    @NotNull
    @Override
    public ToolbarComponents init() {
      final Project project = myMergeContext.getProject();
      final Document document = myMergeRequest.getDocument();

      DiffUtil.executeWriteCommand(document, project, "Init merge content", new Runnable() {
        @Override
        public void run() {
          document.setText(myMergeRequest.getPatchedContent());

          UndoManager undoManager = project != null ? UndoManager.getInstance(project) : UndoManager.getGlobalInstance();
          if (undoManager != null) {
            DocumentReference ref = DocumentReferenceManager.getInstance().create(document);
            undoManager.nonundoableActionPerformed(ref, false);
          }
        }
      });

      ToolbarComponents components = new ToolbarComponents();

      FrameDiffTool.ToolbarComponents init = myViewer.init();
      components.statusPanel = init.statusPanel;
      components.toolbarActions = init.toolbarActions;

      components.closeHandler = new BooleanGetter() {
        @Override
        public boolean get() {
          return MergeUtil.showExitWithoutApplyingChangesDialog(MyViewer.this, myMergeRequest, myMergeContext);
        }
      };
      return components;
    }

    @Nullable
    @Override
    public Action getResolveAction(@NotNull final MergeResult result) {
      if (result == MergeResult.LEFT || result == MergeResult.RIGHT) return null;

      String caption = MergeUtil.getResolveActionTitle(result, myMergeRequest, myMergeContext);
      return new AbstractAction(caption) {
        @Override
        public void actionPerformed(ActionEvent e) {
          if (result == MergeResult.CANCEL &&
              !MergeUtil.showExitWithoutApplyingChangesDialog(MyViewer.this, myMergeRequest, myMergeContext)) {
            return;
          }
          myMergeContext.finishMerge(result);
        }
      };
    }

    @Override
    public void dispose() {
      Disposer.dispose(myViewer);
    }
  }

  public static class DiffIsApproximateNotification extends EditorNotificationPanel {
    public DiffIsApproximateNotification() {
      myLabel.setText("<html>Couldn't find context for patch. Some fragments were applied at the best possible place. " +
                      "<b>Please check carefully.</b></html>");
    }
  }
}
