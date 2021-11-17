// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.actions;

import com.intellij.ide.DataManager;
import com.intellij.ide.ui.customization.CustomActionsSchema;
import com.intellij.idea.ActionsBundle;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.ex.CustomComponentAction;
import com.intellij.openapi.actionSystem.impl.ActionButtonWithText;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.roots.ui.configuration.actions.IconWithTextAction;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.ui.popup.ListPopup;
import com.intellij.openapi.vcs.VcsActions;
import com.intellij.openapi.vcs.VcsBundle;
import com.intellij.util.ui.JBInsets;
import org.jetbrains.annotations.NotNull;

import javax.swing.FocusManager;
import javax.swing.*;
import java.awt.*;
import java.awt.event.InputEvent;
import java.awt.event.MouseEvent;
import java.util.Objects;

/**
 * Vcs quick popup action which is shown in the new toolbar and has two different presentations
 * depending on vcs repo availability
 */
public class VcsQuickActionsToolbarPopup extends IconWithTextAction implements CustomComponentAction, DumbAware {

  public VcsQuickActionsToolbarPopup() {
    getTemplatePresentation().setText(ActionsBundle.message("action.VcsQuickActionsToolbarPopup.text"));
  }

  @NotNull
  @Override
  public JComponent createCustomComponent(@NotNull Presentation presentation, @NotNull String place) {
    return new ActionButtonWithText(this, presentation, place, ActionToolbar.DEFAULT_MINIMUM_BUTTON_SIZE) {
      @Override
      public Color getInactiveTextColor() {
        return getForeground();
      }

      @Override
      public Insets getInsets() {
        return new JBInsets(0, 0, 0, 0);
      }
    };
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    DefaultActionGroup group = new DefaultActionGroup();
    group.add(Objects.requireNonNull(
      CustomActionsSchema.getInstance().getCorrectedAction(VcsActions.VCS_OPERATIONS_POPUP)));

    if (group.getChildrenCount() == 0) return;

    var dataContext = DataManager.getInstance().getDataContext(FocusManager.getCurrentManager().getFocusOwner());
    ListPopup popup = JBPopupFactory.getInstance().createActionGroupPopup(
      VcsBundle.message("action.Vcs.Toolbar.QuickListPopupAction.text"),
      group, dataContext, JBPopupFactory.ActionSelectionAid.NUMBERING, true, null, -1,
      action -> true, ActionPlaces.RUN_TOOLBAR_LEFT_SIDE);

    showPopup(e, popup);
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    if (e.getPlace() != ActionPlaces.MAIN_TOOLBAR) {
      e.getPresentation().setEnabledAndVisible(false);
      return;
    }
  }

  private static void showPopup(@NotNull AnActionEvent e, @NotNull ListPopup popup) {
    InputEvent mouseEvent = e.getInputEvent();
    if (mouseEvent instanceof MouseEvent) {
      Object source = mouseEvent.getSource();
      if (source instanceof JComponent) {
        Point topLeftCorner = ((JComponent)source).getLocationOnScreen();
        Point bottomLeftCorner = new Point(topLeftCorner.x, topLeftCorner.y + ((JComponent)source).getHeight());
        popup.setLocation(bottomLeftCorner);
        popup.show((JComponent)source);
      }
    }
  }
}
