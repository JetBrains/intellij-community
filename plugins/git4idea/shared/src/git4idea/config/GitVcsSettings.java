// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.config;

import com.intellij.dvcs.branch.DvcsBranchInfo;
import com.intellij.dvcs.branch.DvcsBranchSettings;
import com.intellij.dvcs.branch.DvcsCompareSettings;
import com.intellij.dvcs.branch.DvcsSyncSettings;
import com.intellij.openapi.components.SimplePersistentStateComponent;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.components.StoragePathMacros;
import com.intellij.openapi.project.Project;
import com.intellij.util.ArrayUtilRt;
import com.intellij.util.messages.Topic;
import com.intellij.util.xmlb.annotations.Attribute;
import com.intellij.util.xmlb.annotations.Tag;
import git4idea.push.GitPushTagMode;
import git4idea.reset.GitResetMode;
import org.jetbrains.annotations.ApiStatus;
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

  private final Project project;

  public GitVcsSettings(Project project) {
    super(new GitVcsOptions());
    this.project = project;
  }

  public static GitVcsSettings getInstance(Project project) {
    return project.getService(GitVcsSettings.class);
  }

  public @NotNull UpdateMethod getUpdateMethod() {
    return getState().getUpdateMethod();
  }

  public void setUpdateMethod(UpdateMethod updateType) {
    getState().setUpdateMethod(updateType);
  }

  public @NotNull GitSaveChangesPolicy getSaveChangesPolicy() {
    return getState().getSaveChangesPolicy();
  }

  public void setSaveChangesPolicy(GitSaveChangesPolicy value) {
    getState().setSaveChangesPolicy(value);
  }

  /**
   * Save an author of the commit and make it the first one. If amount of authors exceeds the limit, remove least recently selected author.
   *
   * @param authorAsString an author to save
   */
  public void saveCommitAuthor(@NotNull String authorAsString) {
    List<String> previousCommitAuthors = getState().getPreviousCommitAuthors();
    previousCommitAuthors.remove(authorAsString);
    while (previousCommitAuthors.size() >= PREVIOUS_COMMIT_AUTHORS_LIMIT) {
      previousCommitAuthors.remove(previousCommitAuthors.size() - 1);
    }
    previousCommitAuthors.add(0, authorAsString);
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

  public @Nullable String getPathToGit() {
    return getState().getPathToGit();
  }

  public void setPathToGit(@Nullable String value) {
    getState().setPathToGit(value);
    project.getMessageBus().syncPublisher(GitVcsSettingsListener.TOPIC).pathToGitChanged();
  }

  public boolean autoUpdateIfPushRejected() {
    return getState().isPushAutoUpdate();
  }

  public void setAutoUpdateIfPushRejected(boolean value) {
    getState().setPushAutoUpdate(value);
  }

  @Override
  public @NotNull Value getSyncSetting() {
    return getState().getRootSync();
  }

  @Override
  public void setSyncSetting(@NotNull Value value) {
    getState().setRootSync(value);
  }

  public @Nullable String getRecentRootPath() {
    return getState().getRecentGitRootPath();
  }

  public void setRecentRoot(@NotNull String value) {
    getState().setRecentGitRootPath(value);
  }

  public @NotNull Map<String, String> getRecentBranchesByRepository() {
    return getState().getRecentBranchByRepository();
  }

  public void setRecentBranchOfRepository(@NotNull String repositoryPath, @NotNull String branch) {
    getState().getRecentBranchByRepository().put(repositoryPath, branch);
  }

  public @Nullable String getRecentCommonBranch() {
    return getState().getRecentCommonBranch();
  }

  public void setRecentCommonBranch(@NotNull String value) {
    getState().setRecentCommonBranch(value);
  }

  public boolean showRecentBranches() {
    return getState().getShowRecentBranches();
  }

  public void setShowRecentBranches(boolean value) {
    getState().setShowRecentBranches(value);
  }

  public boolean showTags() {
    return getState().getShowTags();
  }

  public void setShowTags(boolean value) {
    getState().setShowTags(value);
    project.getMessageBus().syncPublisher(GitVcsSettingsListener.TOPIC).showTagsChanged(value);
  }

  public boolean filterByActionInPopup() {
    return getState().getFilterByActionInPopup();
  }

  public void setFilterByActionInPopup(boolean value) {
    getState().setFilterByActionInPopup(value);
  }

  public boolean filterByRepositoryInPopup() {
    return getState().getFilterByRepositoryInPopup();
  }

  public void setFilterByRepositoryInPopup(boolean value) {
    getState().setFilterByRepositoryInPopup(value);
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

  public boolean warnAboutLargeFiles() {
    return getState().isWarnAboutLargeFiles();
  }

  public void setWarnAboutLargeFiles(boolean value) {
    getState().setWarnAboutLargeFiles(value);
  }

  public boolean warnAboutBadFileNames() {
    return getState().isWarnAboutBadFileNames();
  }

  public void setWarnAboutLargeFilesLimitMb(int value) {
    getState().setWarnAboutLargeFilesLimitMb(value);
  }

  public int getWarnAboutLargeFilesLimitMb() {
    return getState().getWarnAboutLargeFilesLimitMb();
  }

  public void setWarnAboutBadFileNames(boolean value) {
    getState().setWarnAboutBadFileNames(value);
  }

  public @Nullable GitResetMode getResetMode() {
    return getState().getResetMode();
  }

  public void setResetMode(@NotNull GitResetMode mode) {
    getState().setResetMode(mode);
  }

  public @Nullable GitPushTagMode getPushTagMode() {
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

  public @NotNull GitIncomingCheckStrategy getIncomingCheckStrategy() {
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

  public @NotNull DvcsBranchSettings getBranchSettings() {
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

  @ApiStatus.Internal
  public interface GitVcsSettingsListener {
    @Topic.ProjectLevel
    Topic<GitVcsSettingsListener> TOPIC = new Topic<>(GitVcsSettingsListener.class, Topic.BroadcastDirection.NONE);

    void showTagsChanged(boolean value);

    void pathToGitChanged();
  }
}
