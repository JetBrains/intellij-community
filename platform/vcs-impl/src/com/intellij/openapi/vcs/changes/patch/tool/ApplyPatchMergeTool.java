// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.changes.patch.tool;

import com.intellij.diff.DiffContext;
import com.intellij.diff.merge.*;
import com.intellij.diff.util.DiffUtil;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diff.DiffBundle;
import com.intellij.openapi.ui.Messages;
import com.intellij.ui.awt.RelativePoint;
import com.intellij.ui.scale.JBUIScale;
import com.intellij.util.containers.ContainerUtil;
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

    MyApplyPatchViewer(@NotNull MergeContext context, @NotNull ApplyPatchMergeRequest request) {
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

      components.closeHandler = () -> MergeUtil.showExitWithoutApplyingChangesDialog(this, myMergeRequest, myMergeContext, true);
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
              !MergeUtil.showExitWithoutApplyingChangesDialog(MyApplyPatchViewer.this, myMergeRequest, myMergeContext, true)) {
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
          int yOffset = new RelativePoint(getResultEditor().getComponent(), new Point(0, JBUIScale.scale(5))).getPoint(component).y;
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
