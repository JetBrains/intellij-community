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
import com.intellij.diff.merge.*;
import com.intellij.diff.util.DiffUtil;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diff.DiffBundle;
import com.intellij.openapi.ui.Messages;
import com.intellij.ui.awt.RelativePoint;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.JBUI;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
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
      super(createWrapperDiffContext(context), request);
      myMergeContext = context;
      myMergeRequest = request;
    }

    @NotNull
    private static DiffContext createWrapperDiffContext(@NotNull MergeContext mergeContext) {
      return new MergeUtil.ProxyDiffContext(mergeContext);
    }

    @NotNull
    @Override
    public ToolbarComponents init() {
      initPatchViewer();

      ToolbarComponents components = new ToolbarComponents();
      components.statusPanel = getStatusPanel();
      components.toolbarActions = createToolbarActions();

      components.closeHandler = () -> MergeUtil.showExitWithoutApplyingChangesDialog(this, myMergeRequest, myMergeContext);
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
          if (result == MergeResult.RESOLVED) {
            int unresolved = getUnresolvedCount();
            if (unresolved != 0 &&
                Messages.showYesNoDialog(getComponent().getRootPane(),
                                         DiffBundle.message("apply.patch.partially.resolved.changes.confirmation.message", unresolved),
                                         DiffBundle.message("apply.partially.resolved.merge.dialog.title"),
                                         Messages.getQuestionIcon()) != Messages.YES) {
              return;
            }
          }

          if (result == MergeResult.CANCEL &&
              !MergeUtil.showExitWithoutApplyingChangesDialog(MyApplyPatchViewer.this, myMergeRequest, myMergeContext)) {
            return;
          }

          myMergeContext.finishMerge(result);
        }
      };
    }

    private int getUnresolvedCount() {
      int count = 0;
      for (ApplyPatchChange change : getPatchChanges()) {
        if (change.isResolved()) continue;
        count++;
      }
      return count;
    }

    @Override
    protected void onChangeResolved() {
      super.onChangeResolved();

      if (!ContainerUtil.exists(getModelChanges(), (c) -> !c.isResolved())) {
        ApplicationManager.getApplication().invokeLater(() -> {
          if (isDisposed()) return;

          JComponent component = getComponent();
          int yOffset = new RelativePoint(getResultEditor().getComponent(), new Point(0, JBUI.scale(5))).getPoint(component).y;
          RelativePoint point = new RelativePoint(component, new Point(component.getWidth() / 2, yOffset));

          String message = DiffBundle.message("apply.patch.all.changes.processed.message.text");
          DiffUtil.showSuccessPopup(message, point, this, () -> {
            if (isDisposed()) return;
            myMergeContext.finishMerge(MergeResult.RESOLVED);
          });
        });
      }
    }
  }
}
