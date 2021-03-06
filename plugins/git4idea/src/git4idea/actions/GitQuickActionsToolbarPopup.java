// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.actions;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.impl.ActionButtonWithText;
import com.intellij.openapi.vcs.VcsBundle;
import com.intellij.openapi.vcs.actions.VcsQuickActionsToolbarPopup;
import com.intellij.util.IconUtil;
import com.intellij.util.ui.JBInsets;
import git4idea.branch.GitBranchUtil;
import git4idea.repo.GitRepository;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;


/**
 * Git implementation of the quick popup action
 */
final class GitQuickActionsToolbarPopup extends VcsQuickActionsToolbarPopup {

  GitQuickActionsToolbarPopup() {
    super();
    getTemplatePresentation().setText(VcsBundle.messagePointer("vcs.quicklist.popup.title"));
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
  public void update(@NotNull AnActionEvent e) {
    var project = e.getProject();
    Presentation presentation = e.getPresentation();
    if (project == null || project.isDisposed() || !project.isOpen()) {
      presentation.setEnabledAndVisible(false);
      return;
    }
    GitRepository repo = GitBranchUtil.getCurrentRepository(project);

    if (repo == null) {
      presentation.setText(VcsBundle.message("version.control.main.configurable.name") + " ");
      presentation.setIcon(AllIcons.Vcs.BranchNode);
    }
    else {
      Icon icon = AllIcons.Actions.More;
      if (icon.getIconWidth() < ActionToolbar.DEFAULT_MINIMUM_BUTTON_SIZE.width) {
        icon = IconUtil.toSize(icon, ActionToolbar.DEFAULT_MINIMUM_BUTTON_SIZE.width,
                               ActionToolbar.DEFAULT_MINIMUM_BUTTON_SIZE.height);
      }
      presentation.setIcon(icon);
      presentation.setText(String::new);
    }

    super.update(e);
  }
}
