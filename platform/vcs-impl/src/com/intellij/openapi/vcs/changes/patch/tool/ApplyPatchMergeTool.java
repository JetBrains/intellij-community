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
package com.intellij.openapi.vcs.changes.patch.tool;

import com.intellij.diff.DiffContext;
import com.intellij.diff.FrameDiffTool;
import com.intellij.diff.merge.*;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.util.BooleanGetter;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.event.ActionEvent;

public class ApplyPatchMergeTool implements MergeTool {
  @NotNull
  @Override
  public MergeViewer createComponent(@NotNull MergeContext context, @NotNull MergeRequest request) {
    return new MyApplyPatchViewer(context, (ApplyPatchMergeRequest)request);
  }

  @Override
  public boolean canShow(@NotNull MergeContext context, @NotNull MergeRequest request) {
    return request instanceof ApplyPatchMergeRequest;
  }

  private static class MyApplyPatchViewer extends ApplyPatchViewer implements MergeViewer {
    @NotNull private final MergeContext myMergeContext;
    @NotNull private final ApplyPatchMergeRequest myMergeRequest;

    public MyApplyPatchViewer(@NotNull MergeContext context, @NotNull ApplyPatchMergeRequest request) {
      super(createWrapperDiffContext(context), createWrapperDiffRequest(request), request.getDocument());
      myMergeContext = context;
      myMergeRequest = request;
    }

    @NotNull
    private static DiffContext createWrapperDiffContext(@NotNull MergeContext mergeContext) {
      return new MergeUtil.ProxyDiffContext(mergeContext);
    }

    @NotNull
    private static ApplyPatchDiffRequest createWrapperDiffRequest(@NotNull ApplyPatchMergeRequest request) {
      VirtualFile file = FileDocumentManager.getInstance().getFile(request.getDocument());
      return new ApplyPatchDiffRequest(request.getPatch(), request.getLocalContent(), file, request.getTitle(),
                                       request.getLocalTitle(), request.getResultTitle(), request.getPatchTitle());
    }

    @NotNull
    @Override
    public ToolbarComponents init() {
      ToolbarComponents components = new ToolbarComponents();

      FrameDiffTool.ToolbarComponents init = super.doInit();
      components.statusPanel = init.statusPanel;
      components.toolbarActions = init.toolbarActions;

      components.closeHandler = new BooleanGetter() {
        @Override
        public boolean get() {
          return MergeUtil.showExitWithoutApplyingChangesDialog(MyApplyPatchViewer.this, myMergeRequest, myMergeContext);
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
              !MergeUtil.showExitWithoutApplyingChangesDialog(MyApplyPatchViewer.this, myMergeRequest, myMergeContext)) {
            return;
          }
          myMergeContext.finishMerge(result);
        }
      };
    }
  }
}
