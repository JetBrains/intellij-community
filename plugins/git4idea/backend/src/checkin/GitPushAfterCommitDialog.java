// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.checkin;

import com.intellij.dvcs.push.ui.PushUtils;
import com.intellij.dvcs.push.ui.VcsPushDialog;
import com.intellij.dvcs.repo.Repository;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.application.TransactionGuard;
import com.intellij.openapi.options.advanced.AdvancedSettings;
import com.intellij.openapi.project.Project;
import com.intellij.util.ModalityUiUtil;
import git4idea.config.GitVcsSettings;
import git4idea.i18n.GitBundle;
import git4idea.push.GitPushDivergenceDetector;
import git4idea.push.GitPushDivergenceTasks;
import git4idea.repo.GitRepository;
import git4idea.repo.GitRepositoryManager;
import kotlin.Unit;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.VisibleForTesting;

import javax.swing.JCheckBox;
import javax.swing.JPanel;
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

  @Override
  protected @NotNull JPanel createOptionsPanel() {
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
    showOrPush(false);
  }

  /**
   * @param diverged true when a push target has commits that are not in the pushed source.
   *                 Then the dialog is always shown, because a plain push fails, and a force push needs the user's decision.
   */
  private void showOrPush(boolean diverged) {
    GitVcsSettings vcsSettings = GitVcsSettings.getInstance(myProject);
    if (shouldShow(vcsSettings.shouldPreviewPushOnCommitAndPush(), vcsSettings.isPreviewPushProtectedOnly(),
                   PushUtils.getProhibitedTarget(this) != null, canPush(), diverged)) {
      show();
    }
    else {
      push(false);
    }
  }

  /**
   * Returns true when Commit and Push shows the push dialog, and false when it pushes without the dialog.
   *
   * @param diverged true when a push target has commits that are not in the pushed source
   */
  @ApiStatus.Internal
  @VisibleForTesting
  public static boolean shouldShow(boolean previewSetting, boolean previewProtectedOnly, boolean hasProtectedBranch, boolean canPush,
                                   boolean diverged) {
    return previewSetting && (!previewProtectedOnly || hasProtectedBranch) || !canPush || diverged;
  }

  /**
   * Returns true when Commit and Push checks the remote before it decides to show the push dialog.
   * The check isn't needed when the dialog is always shown, or when the registry turns the check off.
   */
  @ApiStatus.Internal
  @VisibleForTesting
  public static boolean isRemoteCheckNeeded(@NotNull Project project) {
    GitVcsSettings settings = GitVcsSettings.getInstance(project);
    boolean dialogAlwaysShown = settings.shouldPreviewPushOnCommitAndPush() && !settings.isPreviewPushProtectedOnly();
    return !dialogAlwaysShown && GitPushDivergenceDetector.isEnabled();
  }

  public static void showOrPush(@NotNull Project project, @NotNull Collection<GitRepository> selectedRepositories) {
    ModalityState modality = ModalityState.defaultModalityState();
    TransactionGuard.getInstance().assertWriteSafeContext(modality);

    List<GitRepository> repositories = new ArrayList<>(selectedRepositories);
    if (!isRemoteCheckNeeded(project)) {
      showOrPushLater(project, repositories, modality, false);
      return;
    }
    // the check uses git and the network, so it runs in a task of its own, and doesn't delay the end of the commit
    GitPushDivergenceTasks.getInstance(project)
      .checkDefaultPushInBackground(getPushedRepositories(project, repositories),
                                    diverged -> showOrPushLater(project, repositories, modality, diverged));
  }

  /**
   * Returns the repositories that a push without the dialog pushes.
   * The push dialog selects all repositories when the settings ask to push all repositories with commits, or to sync the branches.
   */
  @ApiStatus.Internal
  @VisibleForTesting
  public static @NotNull List<GitRepository> getPushedRepositories(@NotNull Project project, @NotNull List<GitRepository> repositories) {
    GitRepositoryManager manager = GitRepositoryManager.getInstance(project);
    boolean pushesAll = AdvancedSettings.getBoolean("vcs.push.all.with.commits") || manager.isSyncEnabled();
    return pushesAll ? manager.getRepositories() : repositories;
  }

  private static Unit showOrPushLater(@NotNull Project project,
                                      @NotNull List<GitRepository> repositories,
                                      @NotNull ModalityState modality,
                                      boolean diverged) {
    ModalityUiUtil.invokeLaterIfNeeded(modality, project.getDisposed(), () -> {
      new GitPushAfterCommitDialog(project, repositories, null).showOrPush(diverged);
    });
    return Unit.INSTANCE;
  }
}
