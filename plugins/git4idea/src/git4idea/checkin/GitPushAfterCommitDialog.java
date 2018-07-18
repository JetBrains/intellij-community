// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.checkin;

import com.intellij.dvcs.push.ui.VcsPushDialog;
import com.intellij.dvcs.repo.Repository;
import com.intellij.openapi.project.Project;
import com.intellij.util.ui.JBUI;
import git4idea.config.GitVcsSettings;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.List;

/**
 * @author yole
 */
public class GitPushAfterCommitDialog extends VcsPushDialog {
  private JCheckBox myDontShowAgainCheckbox;

  public GitPushAfterCommitDialog(@NotNull Project project,
                                  @NotNull List<? extends Repository> selectedRepositories,
                                  @Nullable Repository currentRepo) {
    super(project, selectedRepositories, currentRepo);
  }

  @NotNull
  @Override
  protected JPanel createOptionsPanel() {
    myDontShowAgainCheckbox = new JCheckBox("For Commit and Push to non-protected branches, preview commits before push");
    myDontShowAgainCheckbox.setSelected(GitVcsSettings.getInstance(myProject).shouldPreviewPushOnCommitAndPush());

    JPanel basePanel = super.createOptionsPanel();
    if (myController.getProhibitedTarget() != null) {
      return basePanel;
    }

    return JBUI.Panels.simplePanel()
                      .addToCenter(basePanel)
                      .addToBottom(myDontShowAgainCheckbox);
  }

  @Override
  public void push(boolean forcePush) {
    if (!myDontShowAgainCheckbox.isSelected()) {
      GitVcsSettings settings = GitVcsSettings.getInstance(myProject);
      if (settings.shouldPreviewPushOnCommitAndPush()) {
        settings.setPreviewPushProtectedOnly(true);
      }
    }
    super.push(forcePush);
  }

  public void showOrPush() {
    boolean hasProtectedBranch = myController.getProhibitedTarget() != null;
    GitVcsSettings vcsSettings = GitVcsSettings.getInstance(myProject);
    boolean showDialog = vcsSettings.shouldPreviewPushOnCommitAndPush();
    boolean showOnlyProtected = vcsSettings.isPreviewPushProtectedOnly();
    if (showDialog && (!showOnlyProtected || hasProtectedBranch) || !canPush()) {
      show();
    }
    else {
      push(false);
    }
  }
}
