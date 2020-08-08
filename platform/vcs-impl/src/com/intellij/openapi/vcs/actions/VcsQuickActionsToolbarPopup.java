// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.actions;

import com.intellij.ide.actions.QuickSwitchSchemeAction;
import com.intellij.ide.ui.customization.CustomActionsSchema;
import com.intellij.openapi.actionSystem.ActionPlaces;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.ListPopup;
import com.intellij.openapi.vcs.VcsActions;
import com.intellij.openapi.vcs.VcsBundle;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.InputEvent;
import java.awt.event.MouseEvent;
import java.util.Objects;

final class VcsQuickActionsToolbarPopup extends QuickSwitchSchemeAction implements DumbAware {
  VcsQuickActionsToolbarPopup() {
    myActionPlace = ActionPlaces.MAIN_TOOLBAR;
    getTemplatePresentation().setText(VcsBundle.messagePointer("vcs.quicklist.popup.title"));
  }

  @Override
  protected String getPopupTitle(@NotNull AnActionEvent e) {
    return VcsBundle.message("action.Vcs.Toolbar.QuickListPopupAction.text");
  }

  @Override
  protected void fillActions(@Nullable Project project,
                             @NotNull DefaultActionGroup group,
                             @NotNull DataContext dataContext) {
    if (project == null) return;
    CustomActionsSchema schema = CustomActionsSchema.getInstance();
    group.add(Objects.requireNonNull(schema.getCorrectedAction(VcsActions.VCS_OPERATIONS_POPUP)));
  }

  @Override
  protected void showPopup(AnActionEvent e, ListPopup popup) {
    InputEvent mouseEvent = e.getInputEvent();
    if (mouseEvent instanceof MouseEvent) {
      Object source = mouseEvent.getSource();
      if (source instanceof JComponent) {
        Point topLeftCorner = ((JComponent)source).getLocationOnScreen();
        Point bottomLeftCorner = new Point(topLeftCorner.x, topLeftCorner.y + ((JComponent)source).getHeight());
        popup.setLocation(bottomLeftCorner);
      }
    }
    super.showPopup(e, popup);
  }
}
