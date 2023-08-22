// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.config;

import com.intellij.dvcs.branch.DvcsBranchInfo;
import com.intellij.dvcs.branch.DvcsBranchSettings;
import com.intellij.dvcs.branch.DvcsCompareSettings;
import com.intellij.dvcs.branch.DvcsSyncSettings;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.SimplePersistentStateComponent;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.components.StoragePathMacros;
import com.intellij.openapi.project.Project;
import com.intellij.util.ArrayUtilRt;
import com.intellij.util.xmlb.annotations.Attribute;
import com.intellij.util.xmlb.annotations.Tag;
import com.intellij.vcs.log.VcsUser;
import git4idea.push.GitPushTagMode;
import git4idea.reset.GitResetMode;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Git VCS settings
 */
@State(name = "Git.Settings", storages = @Storage(StoragePathMacros.WORKSPACE_FILE))
public final class GitVcsSettings extends SimplePersistentStateComponent<GitVcsOptions> implements DvcsSyncSettings, DvcsCompareSettings {
  private static final int PREVIOUS_COMMIT_AUTHORS_LIMIT = 16; // Limit for previous commit authors

  public GitVcsSettings() {
    super(new GitVcsOptions());
  }

  public GitVcsApplicationSettings getAppSettings() {
    return GitVcsApplicationSettings.getInstance();
  }

  public static GitVcsSettings getInstance(Project project) {
    return project.getService(GitVcsSettings.class);
  }

  @NotNull
  public UpdateMethod getUpdateMethod() {
    return getState().getUpdateMethod();
  }

  public void setUpdateMethod(UpdateMethod updateType) {
    getState().setUpdateMethod(updateType);
  }

  @NotNull
  public GitSaveChangesPolicy getSaveChangesPolicy() {
    return getState().getSaveChangesPolicy();
  }

  public void setSaveChangesPolicy(GitSaveChangesPolicy value) {
    getState().setSaveChangesPolicy(value);
  }

  /**
   * Save an author of the commit and make it the first one. If amount of authors exceeds the limit, remove least recently selected author.
   *
   * @param author an author to save
   */
  public void saveCommitAuthor(@NotNull VcsUser author) {
    List<String> previousCommitAuthors = getState().getPreviousCommitAuthors();
    previousCommitAuthors.remove(author.toString());
    while (previousCommitAuthors.size() >= PREVIOUS_COMMIT_AUTHORS_LIMIT) {
      previousCommitAuthors.remove(previousCommitAuthors.size() - 1);
    }
    previousCommitAuthors.add(0, author.toString());
  }

  public String[] getCommitAuthors() {
    return ArrayUtilRt.toStringArray(getState().getPreviousCommitAuthors());
  }

  @Override
  public void loadState(@NotNull GitVcsOptions state) {
    super.loadState(state);
    migrateUpdateIncomingBranchInfo(state);
  }

  private static void migrateUpdateIncomingBranchInfo(@NotNull GitVcsOptions state) {
    if (!state.isUpdateBranchesInfo()) {
      state.setIncomingCheckStrategy(GitIncomingCheckStrategy.Never);
      //set default value
      state.setUpdateBranchesInfo(true);
    }
  }

  @Nullable
  public String getPathToGit() {
    return getState().getPathToGit();
  }

  public void setPathToGit(@Nullable String value) {
    getState().setPathToGit(value);
    ApplicationManager.getApplication().getMessageBus().syncPublisher(GitExecutableManager.TOPIC).executableChanged();
  }

  public boolean autoUpdateIfPushRejected() {
    return getState().isPushAutoUpdate();
  }

  public void setAutoUpdateIfPushRejected(boolean value) {
    getState().setPushAutoUpdate(value);
  }

  @Override
  @NotNull
  public Value getSyncSetting() {
    return getState().getRootSync();
  }

  @Override
  public void setSyncSetting(@NotNull Value value) {
    getState().setRootSync(value);
  }

  @Nullable
  public String getRecentRootPath() {
    return getState().getRecentGitRootPath();
  }

  public void setRecentRoot(@NotNull String value) {
    getState().setRecentGitRootPath(value);
  }

  @NotNull
  public Map<String, String> getRecentBranchesByRepository() {
    return getState().getRecentBranchByRepository();
  }

  public void setRecentBranchOfRepository(@NotNull String repositoryPath, @NotNull String branch) {
    getState().getRecentBranchByRepository().put(repositoryPath, branch);
  }

  @Nullable
  public String getRecentCommonBranch() {
    return getState().getRecentCommonBranch();
  }

  public void setRecentCommonBranch(@NotNull String value) {
    getState().setRecentCommonBranch(value);
  }

  public boolean warnAboutCrlf() {
    return getState().getWarnAboutCrlf();
  }

  public void setWarnAboutCrlf(boolean value) {
    getState().setWarnAboutCrlf(value);
  }

  public boolean warnAboutDetachedHead() {
    return getState().isWarnAboutDetachedHead();
  }

  public void setWarnAboutDetachedHead(boolean value) {
    getState().setWarnAboutDetachedHead(value);
  }

  @Nullable
  public GitResetMode getResetMode() {
    return getState().getResetMode();
  }

  public void setResetMode(@NotNull GitResetMode mode) {
    getState().setResetMode(mode);
  }

  @Nullable
  public GitPushTagMode getPushTagMode() {
    return getState().getPushTags();
  }

  public void setPushTagMode(@Nullable GitPushTagMode value) {
    getState().setPushTags(value);
  }

  public boolean shouldSignOffCommit() {
    return getState().isSignOffCommit();
  }

  public void setSignOffCommit(boolean value) {
    getState().setSignOffCommit(value);
  }

  @NotNull
  public GitIncomingCheckStrategy getIncomingCheckStrategy() {
    return getState().getIncomingCheckStrategy();
  }

  public void setIncomingCheckStrategy(@NotNull GitIncomingCheckStrategy strategy) {
    getState().setIncomingCheckStrategy(strategy);
  }

  public boolean shouldPreviewPushOnCommitAndPush() {
    return getState().isPreviewPushOnCommitAndPush();
  }

  public void setPreviewPushOnCommitAndPush(boolean value) {
    getState().setPreviewPushOnCommitAndPush(value);
  }

  public boolean isPreviewPushProtectedOnly() {
    return getState().isPreviewPushProtectedOnly();
  }

  public void setPreviewPushProtectedOnly(boolean value) {
    getState().setPreviewPushProtectedOnly(value);
  }

  public boolean isCommitRenamesSeparately() {
    return getState().isCommitRenamesSeparately();
  }

  public void setCommitRenamesSeparately(boolean value) {
    getState().setCommitRenamesSeparately(value);
  }

  @NotNull
  public DvcsBranchSettings getBranchSettings() {
    return getState().getBranchSettings();
  }

  public boolean shouldSetUserNameGlobally() {
    return getState().isSetUserNameGlobally();
  }

  public void setUserNameGlobally(boolean value) {
    getState().setSetUserNameGlobally(value);
  }

  @Override
  public boolean shouldSwapSidesInCompareBranches() {
    return getState().isSwapSidesInCompareBranches();
  }

  @Override
  public void setSwapSidesInCompareBranches(boolean value) {
    getState().setSwapSidesInCompareBranches(value);
  }

  public boolean shouldAddSuffixToCherryPicksOfPublishedCommits() {
    return getState().isAddSuffixToCherryPicksOfPublishedCommits();
  }

  public void setAddSuffixToCherryPicks(boolean value) {
    getState().setAddSuffixToCherryPicksOfPublishedCommits(value);
  }

  @Tag("push-target-info")
  private static class PushTargetInfo extends DvcsBranchInfo {
    @Attribute(value = "target-remote") public String targetRemoteName;
    @Attribute(value = "target-branch") public String targetBranchName;

    @SuppressWarnings("unused")
    PushTargetInfo() {
      this("", "", "", "");
    }

    PushTargetInfo(@NotNull String repositoryPath, @NotNull String source, @NotNull String targetRemote, @NotNull String targetBranch) {
      super(repositoryPath, source);
      targetRemoteName = targetRemote;
      targetBranchName = targetBranch;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;
      if (!super.equals(o)) return false;

      PushTargetInfo info = (PushTargetInfo)o;

      if (targetRemoteName != null ? !targetRemoteName.equals(info.targetRemoteName) : info.targetRemoteName != null) return false;
      if (targetBranchName != null ? !targetBranchName.equals(info.targetBranchName) : info.targetBranchName != null) return false;

      return true;
    }

    @Override
    public int hashCode() {
      return Objects.hash(super.hashCode(), targetRemoteName, targetBranchName);
    }
  }
}
