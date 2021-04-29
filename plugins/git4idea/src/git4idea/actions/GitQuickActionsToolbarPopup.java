// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.actions;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.ActionToolbar;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.actionSystem.impl.ActionButtonWithText;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.NlsActions;
import com.intellij.openapi.vcs.actions.VcsQuickActionsToolbarPopup;
import com.intellij.util.IconUtil;
import com.intellij.util.ui.JBInsets;
import git4idea.branch.GitBranchUtil;
import git4idea.i18n.GitBundle;
import git4idea.repo.GitRepository;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;


/**
 * Git implementation of the quick popup action
 */
final class GitQuickActionsToolbarPopup extends VcsQuickActionsToolbarPopup {
  private static final Key<Boolean> KEY_ICON_WITH_TEXT = Key.create("KEY_ICON_WITH_TEXT");

  GitQuickActionsToolbarPopup() {
    super();
    getTemplatePresentation().setText(GitBundle.message("action.Vcs.ShowMoreActions.text"));
  }

  private static class MyActionButtonWithText extends ActionButtonWithText {

    private MyActionButtonWithText(AnAction action,
                                   Presentation presentation,
                                   String place,
                                   Dimension minimumSize) {
      super(action, presentation, place, minimumSize);
    }

    @Override
    public @NotNull @NlsActions.ActionText String getText() {
      boolean iconWithText = myPresentation.getClientProperty(KEY_ICON_WITH_TEXT);
      if (iconWithText) {
        return super.getText() + " ";
      }
      else {
        return "";
      }
    }

    @Override
    public Color getInactiveTextColor() {
      return getForeground();
    }

    @Override
    public Insets getInsets() {
      return new JBInsets(0, 0, 0, 0);
    }
  }

  @NotNull
  @Override
  public JComponent createCustomComponent(@NotNull Presentation presentation, @NotNull String place) {
    return new MyActionButtonWithText(this, presentation, place, ActionToolbar.DEFAULT_MINIMUM_BUTTON_SIZE);
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    var project = e.getProject();
    var presentation = e.getPresentation();

    if (project == null || project.isDisposed() || !project.isOpen()) {
      presentation.setEnabledAndVisible(false);
      return;
    }
    GitRepository repo = GitBranchUtil.getCurrentRepository(project);

    if (repo == null) {
      presentation.putClientProperty(KEY_ICON_WITH_TEXT, true);
      presentation.setIcon(AllIcons.Vcs.BranchNode);
    }
    else {
      Icon icon = AllIcons.Actions.More;
      if (icon.getIconWidth() < ActionToolbar.DEFAULT_MINIMUM_BUTTON_SIZE.width) {
        icon = IconUtil.toSize(icon, ActionToolbar.DEFAULT_MINIMUM_BUTTON_SIZE.width,
                               ActionToolbar.DEFAULT_MINIMUM_BUTTON_SIZE.height);
      }
      presentation.putClientProperty(KEY_ICON_WITH_TEXT, false);
      presentation.setIcon(icon);
    }

    super.update(e);
  }
}
