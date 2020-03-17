// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.ui.branch;

import com.intellij.dvcs.DvcsUtil;
import com.intellij.dvcs.branch.DvcsSyncSettings;
import com.intellij.dvcs.repo.Repository;
import com.intellij.dvcs.ui.DvcsStatusWidget;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.ListPopup;
import com.intellij.openapi.wm.StatusBarWidget;
import com.intellij.ui.LayeredIcon;
import git4idea.GitUtil;
import git4idea.GitVcs;
import git4idea.branch.GitBranchIncomingOutgoingManager;
import git4idea.branch.GitBranchUtil;
import git4idea.config.GitVcsSettings;
import git4idea.repo.GitRepository;
import git4idea.repo.GitRepositoryManager;
import icons.DvcsImplIcons;
import org.jetbrains.annotations.CalledInAwt;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

/**
 * Status bar widget which displays the current branch for the file currently open in the editor.
 */
public class GitBranchWidget extends DvcsStatusWidget<GitRepository> {
  private static final Icon INCOMING_LAYERED = new LayeredIcon(AllIcons.Vcs.Branch, DvcsImplIcons.IncomingLayer);
  private static final Icon INCOMING_OUTGOING_LAYERED = new LayeredIcon(AllIcons.Vcs.Branch, DvcsImplIcons.IncomingOutgoingLayer);
  private static final Icon OUTGOING_LAYERED = new LayeredIcon(AllIcons.Vcs.Branch, DvcsImplIcons.OutgoingLayer);
  private final GitVcsSettings mySettings;

  public GitBranchWidget(@NotNull Project project) {
    super(project, GitVcs.NAME);
    mySettings = GitVcsSettings.getInstance(project);
  }

  @Override
  public StatusBarWidget copy() {
    return new GitBranchWidget(getProject());
  }

  @Nullable
  @Override
  @CalledInAwt
  protected GitRepository guessCurrentRepository(@NotNull Project project) {
    return DvcsUtil.guessCurrentRepositoryQuick(project, GitUtil.getRepositoryManager(project), mySettings.getRecentRootPath());
  }

  @Nullable
  @Override
  protected Icon getIcon(@NotNull GitRepository repository) {
    String currentBranchName = repository.getCurrentBranchName();
    if (repository.getState() == Repository.State.NORMAL && currentBranchName != null) {
      GitRepository indicatorRepo =
        (GitRepositoryManager.getInstance(myProject).moreThanOneRoot() && mySettings.getSyncSetting() == DvcsSyncSettings.Value.DONT_SYNC)
        ? repository
        : null;
      boolean hasIncoming = GitBranchIncomingOutgoingManager.getInstance(myProject).hasIncomingFor(indicatorRepo, currentBranchName);
      boolean hasOutgoing = GitBranchIncomingOutgoingManager.getInstance(myProject).hasOutgoingFor(indicatorRepo, currentBranchName);
      if (hasIncoming) {
        return hasOutgoing ? INCOMING_OUTGOING_LAYERED : INCOMING_LAYERED;
      }
      else if (hasOutgoing) return OUTGOING_LAYERED;
    }
    return super.getIcon(repository);
  }

  @NotNull
  @Override
  protected String getFullBranchName(@NotNull GitRepository repository) {
    return GitBranchUtil.getDisplayableBranchText(repository);
  }

  @Override
  protected boolean isMultiRoot(@NotNull Project project) {
    return !GitUtil.justOneGitRepository(project);
  }

  @NotNull
  @Override
  protected ListPopup getPopup(@NotNull Project project, @NotNull GitRepository repository) {
    return GitBranchPopup.getInstance(project, repository).asListPopup();
  }

  @Override
  protected void subscribeToRepoChangeEvents(@NotNull Project project) {
    project.getMessageBus().connect().subscribe(GitRepository.GIT_REPO_CHANGE, r -> {
      LOG.debug("repository changed");
      updateLater();
    });
    project.getMessageBus().connect().subscribe(GitBranchIncomingOutgoingManager.GIT_INCOMING_OUTGOING_CHANGED, () -> updateLater());
  }

  @Override
  protected void rememberRecentRoot(@NotNull String path) {
    mySettings.setRecentRoot(path);
  }
}
