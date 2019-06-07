// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.config;

import com.intellij.dvcs.branch.DvcsBranchInfo;
import com.intellij.dvcs.branch.DvcsBranchSettings;
import com.intellij.dvcs.branch.DvcsCompareSettings;
import com.intellij.dvcs.branch.DvcsSyncSettings;
import com.intellij.openapi.components.*;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.ModificationTracker;
import com.intellij.util.ArrayUtilRt;
import com.intellij.util.xmlb.annotations.Attribute;
import com.intellij.util.xmlb.annotations.Tag;
import git4idea.push.GitPushTagMode;
import git4idea.reset.GitResetMode;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;
import java.util.Objects;

import static git4idea.config.GitIncomingCheckStrategy.Never;

/**
 * Git VCS settings
 */
@State(name = "Git.Settings", storages = @Storage(StoragePathMacros.WORKSPACE_FILE))
public class GitVcsSettings implements PersistentStateComponent<GitVcsOptions>, DvcsSyncSettings, DvcsCompareSettings, ModificationTracker {
  private static final int PREVIOUS_COMMIT_AUTHORS_LIMIT = 16; // Limit for previous commit authors

  private final GitVcsApplicationSettings myAppSettings;
  private GitVcsOptions myState = new GitVcsOptions();

  /**
   * The way the local changes are saved before update if user has selected auto-stash
   */
  public enum UpdateChangesPolicy {
    STASH,
    SHELVE,
  }

  public GitVcsSettings(GitVcsApplicationSettings appSettings) {
    myAppSettings = appSettings;
  }

  public GitVcsApplicationSettings getAppSettings() {
    return myAppSettings;
  }

  public static GitVcsSettings getInstance(Project project) {
    return ServiceManager.getService(project, GitVcsSettings.class);
  }

  @NotNull
  public UpdateMethod getUpdateMethod() {
    return myState.getUpdateMethod();
  }

  public void setUpdateMethod(UpdateMethod updateType) {
    myState.setUpdateMethod(updateType);
  }

  @NotNull
  public UpdateChangesPolicy updateChangesPolicy() {
    return myState.getUpdateChangesPolicy();
  }

  public void setUpdateChangesPolicy(UpdateChangesPolicy value) {
    myState.setUpdateChangesPolicy(value);
  }

  /**
   * Save an author of the commit and make it the first one. If amount of authors exceeds the limit, remove least recently selected author.
   *
   * @param author an author to save
   */
  public void saveCommitAuthor(String author) {
    List<String> previousCommitAuthors = myState.getPreviousCommitAuthors();
    previousCommitAuthors.remove(author);
    while (previousCommitAuthors.size() >= PREVIOUS_COMMIT_AUTHORS_LIMIT) {
      previousCommitAuthors.remove(previousCommitAuthors.size() - 1);
    }
    previousCommitAuthors.add(0, author);
  }

  public String[] getCommitAuthors() {
    return ArrayUtilRt.toStringArray(myState.getPreviousCommitAuthors());
  }

  @Override
  public long getModificationCount() {
    return myState.getModificationCount();
  }

  @Override
  public GitVcsOptions getState() {
    return myState;
  }

  @Override
  public void loadState(@NotNull GitVcsOptions state) {
    myState = state;
    migrateUpdateIncomingBranchInfo(state);
  }

  private void migrateUpdateIncomingBranchInfo(@NotNull GitVcsOptions state) {
    if (!state.isUpdateBranchesInfo()) {
      myState.setIncomingCheckStrategy(Never);
      //set default value
      myState.setUpdateBranchesInfo(true);
    }
  }

  @Nullable
  public String getPathToGit() {
    return myState.getPathToGit();
  }

  public void setPathToGit(@Nullable String value) {
    myState.setPathToGit(value);
  }

  public boolean autoUpdateIfPushRejected() {
    return myState.isPushAutoUpdate();
  }

  public void setAutoUpdateIfPushRejected(boolean value) {
    myState.setPushAutoUpdate(value);
  }

  public boolean shouldUpdateAllRootsIfPushRejected() {
    return myState.isPushUpdateAllRoots();
  }

  public void setUpdateAllRootsIfPushRejected(boolean value) {
    myState.setPushUpdateAllRoots(value);
  }

  @Override
  @NotNull
  public Value getSyncSetting() {
    return myState.getRootSync();
  }

  @Override
  public void setSyncSetting(@NotNull Value value) {
    myState.setRootSync(value);
  }

  @Nullable
  public String getRecentRootPath() {
    return myState.getRecentGitRootPath();
  }

  public void setRecentRoot(@NotNull String value) {
    myState.setRecentGitRootPath(value);
  }

  @NotNull
  public Map<String, String> getRecentBranchesByRepository() {
    return myState.getRecentBranchByRepository();
  }

  public void setRecentBranchOfRepository(@NotNull String repositoryPath, @NotNull String branch) {
    myState.getRecentBranchByRepository().put(repositoryPath, branch);
  }

  @Nullable
  public String getRecentCommonBranch() {
    return myState.getRecentCommonBranch();
  }

  public void setRecentCommonBranch(@NotNull String value) {
    myState.setRecentCommonBranch(value);
  }

  public void setAutoCommitOnRevert(boolean value) {
    myState.setAutoCommitOnRevert(value);
  }

  public boolean isAutoCommitOnRevert() {
    return myState.isAutoCommitOnRevert();
  }

  public boolean warnAboutCrlf() {
    return myState.getWarnAboutCrlf();
  }

  public void setWarnAboutCrlf(boolean value) {
    myState.setWarnAboutCrlf(value);
  }

  public boolean warnAboutDetachedHead() {
    return myState.isWarnAboutDetachedHead();
  }

  public void setWarnAboutDetachedHead(boolean value) {
    myState.setWarnAboutDetachedHead(value);
  }

  @Nullable
  public GitResetMode getResetMode() {
    return myState.getResetMode();
  }

  public void setResetMode(@NotNull GitResetMode mode) {
    myState.setResetMode(mode);
  }

  @Nullable
  public GitPushTagMode getPushTagMode() {
    return myState.getPushTags();
  }

  public void setPushTagMode(@Nullable GitPushTagMode value) {
    myState.setPushTags(value);
  }

  public boolean shouldSignOffCommit() {
    return myState.isSignOffCommit();
  }

  public void setSignOffCommit(boolean value) {
    myState.setSignOffCommit(value);
  }

  @NotNull
  public GitIncomingCheckStrategy getIncomingCheckStrategy() {
    return myState.getIncomingCheckStrategy();
  }

  public void setIncomingCheckStrategy(@NotNull GitIncomingCheckStrategy strategy) {
    myState.setIncomingCheckStrategy(strategy);
  }

  public boolean shouldPreviewPushOnCommitAndPush() {
    return myState.isPreviewPushOnCommitAndPush();
  }

  public void setPreviewPushOnCommitAndPush(boolean value) {
    myState.setPreviewPushOnCommitAndPush(value);
  }

  public boolean isPreviewPushProtectedOnly() {
    return myState.isPreviewPushProtectedOnly();
  }

  public void setPreviewPushProtectedOnly(boolean value) {
    myState.setPreviewPushProtectedOnly(value);
  }

  public boolean isCommitRenamesSeparately() {
    return myState.isCommitRenamesSeparately();
  }

  public void setCommitRenamesSeparately(boolean value) {
    myState.setCommitRenamesSeparately(value);
  }

  @NotNull
  public DvcsBranchSettings getFavoriteBranchSettings() {
    return myState.getFavoriteBranchSettings();
  }

  public boolean shouldSetUserNameGlobally() {
    return myState.isSetUserNameGlobally();
  }

  public void setUserNameGlobally(boolean value) {
    myState.setSetUserNameGlobally(value);
  }

  @Override
  public boolean shouldSwapSidesInCompareBranches() {
    return myState.isSwapSidesInCompareBranches();
  }

  @Override
  public void setSwapSidesInCompareBranches(boolean value) {
    myState.setSwapSidesInCompareBranches(value);
  }

  public boolean shouldAddSuffixToCherryPicksOfPublishedCommits() {
    return myState.isAddSuffixToCherryPicksOfPublishedCommits();
  }

  public void setAddSuffixToCherryPicks(boolean value) {
    myState.setAddSuffixToCherryPicksOfPublishedCommits(value);
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
