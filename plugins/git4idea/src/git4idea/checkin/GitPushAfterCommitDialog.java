// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.checkin;

import com.intellij.dvcs.push.ui.PushUtils;
import com.intellij.dvcs.push.ui.VcsPushDialog;
import com.intellij.dvcs.repo.Repository;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.application.TransactionGuard;
import com.intellij.openapi.project.Project;
import com.intellij.util.ModalityUiUtil;
import git4idea.config.GitVcsSettings;
import git4idea.i18n.GitBundle;
import git4idea.repo.GitRepository;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;


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
    myDontShowAgainCheckbox = new JCheckBox(GitBundle.message("push.dialog.preview.commits.before.push"));
    myDontShowAgainCheckbox.setSelected(GitVcsSettings.getInstance(myProject).shouldPreviewPushOnCommitAndPush());

    JPanel basePanel = super.createOptionsPanel();
    if (PushUtils.getProhibitedTarget(this) != null) {
      return basePanel;
    }

    basePanel.add(myDontShowAgainCheckbox);
    return basePanel;
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
    boolean hasProtectedBranch = PushUtils.getProhibitedTarget(this) != null;
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

  public static void showOrPush(@NotNull Project project, @NotNull Collection<GitRepository> selectedRepositories) {
    ModalityState modality = ModalityState.defaultModalityState();
    TransactionGuard.getInstance().assertWriteSafeContext(modality);

    List<GitRepository> repositories = new ArrayList<>(selectedRepositories);
    ModalityUiUtil.invokeLaterIfNeeded(modality, project.getDisposed(), () -> {
      new GitPushAfterCommitDialog(project, repositories, null).showOrPush();
    });
  }
}
