/*
 * Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */
package git4idea.config;

import com.intellij.dvcs.branch.DvcsBranchInfo;
import com.intellij.dvcs.branch.DvcsBranchSettings;
import com.intellij.dvcs.branch.DvcsSyncSettings;
import com.intellij.openapi.components.*;
import com.intellij.openapi.project.Project;
import com.intellij.util.ArrayUtil;
import com.intellij.util.ObjectUtils;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.xmlb.annotations.Attribute;
import com.intellij.util.xmlb.annotations.Property;
import com.intellij.util.xmlb.annotations.Tag;
import com.intellij.util.xmlb.annotations.XCollection;
import git4idea.GitRemoteBranch;
import git4idea.GitUtil;
import git4idea.push.GitPushTagMode;
import git4idea.repo.GitRemote;
import git4idea.repo.GitRepository;
import git4idea.reset.GitResetMode;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

import static com.intellij.dvcs.branch.DvcsBranchUtil.find;

/**
 * Git VCS settings
 */
@State(name = "Git.Settings", storages = {@Storage(StoragePathMacros.WORKSPACE_FILE)})
public class GitVcsSettings implements PersistentStateComponent<GitVcsSettings.State>, DvcsSyncSettings {

  private static final int PREVIOUS_COMMIT_AUTHORS_LIMIT = 16; // Limit for previous commit authors

  private final GitVcsApplicationSettings myAppSettings;
  private State myState = new State();

  /**
   * The way the local changes are saved before update if user has selected auto-stash
   */
  public enum UpdateChangesPolicy {
    STASH,
    SHELVE,
  }

  public static class State {
    public String PATH_TO_GIT = null;

    // The previously entered authors of the commit (up to {@value #PREVIOUS_COMMIT_AUTHORS_LIMIT})
    public List<String> PREVIOUS_COMMIT_AUTHORS = new ArrayList<>();
    public GitVcsApplicationSettings.SshExecutable SSH_EXECUTABLE = GitVcsApplicationSettings.SshExecutable.IDEA_SSH;
    // The policy that specifies how files are saved before update or rebase
    public UpdateChangesPolicy UPDATE_CHANGES_POLICY = UpdateChangesPolicy.STASH;
    public UpdateMethod UPDATE_TYPE = UpdateMethod.BRANCH_DEFAULT;
    public boolean PUSH_AUTO_UPDATE = false;
    public boolean PUSH_UPDATE_ALL_ROOTS = true;
    public Value ROOT_SYNC = Value.NOT_DECIDED;
    public String RECENT_GIT_ROOT_PATH = null;
    public Map<String, String> RECENT_BRANCH_BY_REPOSITORY = new HashMap<>();
    public String RECENT_COMMON_BRANCH = null;
    public boolean AUTO_COMMIT_ON_CHERRY_PICK = false;
    public boolean AUTO_COMMIT_ON_REVERT = false;
    public boolean WARN_ABOUT_CRLF = true;
    public boolean WARN_ABOUT_DETACHED_HEAD = true;
    public GitResetMode RESET_MODE = null;
    public GitPushTagMode PUSH_TAGS = null;
    public boolean SIGN_OFF_COMMIT = false;
    public boolean SET_USER_NAME_GLOBALLY = true;
    public boolean SWAP_SIDES_IN_COMPARE_BRANCHES = false;

    @XCollection
    @Tag("push-targets")
    public List<PushTargetInfo> PUSH_TARGETS = ContainerUtil.newArrayList();

    @Property(surroundWithTag = false, flat = true)
    public DvcsBranchSettings FAVORITE_BRANCH_SETTINGS = new DvcsBranchSettings();
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
  public UpdateMethod getUpdateType() {
    return ObjectUtils.notNull(myState.UPDATE_TYPE, UpdateMethod.BRANCH_DEFAULT);
  }

  public void setUpdateType(UpdateMethod updateType) {
    myState.UPDATE_TYPE = updateType;
  }

  @NotNull
  public UpdateChangesPolicy updateChangesPolicy() {
    return myState.UPDATE_CHANGES_POLICY;
  }

  public void setUpdateChangesPolicy(UpdateChangesPolicy value) {
    myState.UPDATE_CHANGES_POLICY = value;
  }

  /**
   * Save an author of the commit and make it the first one. If amount of authors exceeds the limit, remove least recently selected author.
   *
   * @param author an author to save
   */
  public void saveCommitAuthor(String author) {
    myState.PREVIOUS_COMMIT_AUTHORS.remove(author);
    while (myState.PREVIOUS_COMMIT_AUTHORS.size() >= PREVIOUS_COMMIT_AUTHORS_LIMIT) {
      myState.PREVIOUS_COMMIT_AUTHORS.remove(myState.PREVIOUS_COMMIT_AUTHORS.size() - 1);
    }
    myState.PREVIOUS_COMMIT_AUTHORS.add(0, author);
  }

  public String[] getCommitAuthors() {
    return ArrayUtil.toStringArray(myState.PREVIOUS_COMMIT_AUTHORS);
  }

  public State getState() {
    return myState;
  }

  public void loadState(State state) {
    myState = state;
  }

  @Nullable
  public String getPathToGit() {
    return myState.PATH_TO_GIT;
  }

  public void setPathToGit(@Nullable String pathToGit) {
    myState.PATH_TO_GIT = pathToGit;
  }

  public boolean autoUpdateIfPushRejected() {
    return myState.PUSH_AUTO_UPDATE;
  }

  public void setAutoUpdateIfPushRejected(boolean autoUpdate) {
    myState.PUSH_AUTO_UPDATE = autoUpdate;
  }

  public boolean shouldUpdateAllRootsIfPushRejected() {
    return myState.PUSH_UPDATE_ALL_ROOTS;
  }

  public void setUpdateAllRootsIfPushRejected(boolean updateAllRoots) {
    myState.PUSH_UPDATE_ALL_ROOTS = updateAllRoots;
  }

  @NotNull
  public Value getSyncSetting() {
    return myState.ROOT_SYNC;
  }

  public void setSyncSetting(@NotNull Value syncSetting) {
    myState.ROOT_SYNC = syncSetting;
  }

  @Nullable
  public String getRecentRootPath() {
    return myState.RECENT_GIT_ROOT_PATH;
  }

  public void setRecentRoot(@NotNull String recentGitRootPath) {
    myState.RECENT_GIT_ROOT_PATH = recentGitRootPath;
  }

  @NotNull
  public Map<String, String> getRecentBranchesByRepository() {
    return myState.RECENT_BRANCH_BY_REPOSITORY;
  }

  public void setRecentBranchOfRepository(@NotNull String repositoryPath, @NotNull String branch) {
    myState.RECENT_BRANCH_BY_REPOSITORY.put(repositoryPath, branch);
  }

  @Nullable
  public String getRecentCommonBranch() {
    return myState.RECENT_COMMON_BRANCH;
  }

  public void setRecentCommonBranch(@NotNull String branch) {
    myState.RECENT_COMMON_BRANCH = branch;
  }

  public void setAutoCommitOnCherryPick(boolean autoCommit) {
    myState.AUTO_COMMIT_ON_CHERRY_PICK = autoCommit;
  }

  public boolean isAutoCommitOnCherryPick() {
    return myState.AUTO_COMMIT_ON_CHERRY_PICK;
  }

  public void setAutoCommitOnRevert(boolean autoCommit) {
    myState.AUTO_COMMIT_ON_REVERT = autoCommit;
  }

  public boolean isAutoCommitOnRevert() {
    return myState.AUTO_COMMIT_ON_REVERT;
  }

  public boolean warnAboutCrlf() {
    return myState.WARN_ABOUT_CRLF;
  }

  public void setWarnAboutCrlf(boolean warn) {
    myState.WARN_ABOUT_CRLF = warn;
  }

  public boolean warnAboutDetachedHead() {
    return myState.WARN_ABOUT_DETACHED_HEAD;
  }

  public void setWarnAboutDetachedHead(boolean warn) {
    myState.WARN_ABOUT_DETACHED_HEAD = warn;
  }

  @Nullable
  public GitResetMode getResetMode() {
    return myState.RESET_MODE;
  }

  public void setResetMode(@NotNull GitResetMode mode) {
    myState.RESET_MODE = mode;
  }

  @Nullable
  public GitPushTagMode getPushTagMode() {
    return myState.PUSH_TAGS;
  }

  public void setPushTagMode(@Nullable GitPushTagMode mode) {
    myState.PUSH_TAGS = mode;
  }

  public boolean shouldSignOffCommit() {
    return myState.SIGN_OFF_COMMIT;
  }

  public void setSignOffCommit(boolean state) {
    myState.SIGN_OFF_COMMIT = state;
  }

  /**
   * Provides migration from project settings.
   * This method is to be removed in IDEA 13: it should be moved to {@link GitVcsApplicationSettings}
   */
  @Deprecated
  public boolean isIdeaSsh() {
    if (getAppSettings().getIdeaSsh() == null) { // app setting has not been initialized yet => migrate the project setting there
      getAppSettings().setIdeaSsh(myState.SSH_EXECUTABLE);
    }
    return getAppSettings().getIdeaSsh() == GitVcsApplicationSettings.SshExecutable.IDEA_SSH;
  }

  @Nullable
  public GitRemoteBranch getPushTarget(@NotNull GitRepository repository, @NotNull String sourceBranch) {
    PushTargetInfo targetInfo = find(myState.PUSH_TARGETS, repository, sourceBranch);
    if (targetInfo == null) return null;
    GitRemote remote = GitUtil.findRemoteByName(repository, targetInfo.targetRemoteName);
    if (remote == null) return null;
    return GitUtil.findOrCreateRemoteBranch(repository, remote, targetInfo.targetBranchName);
  }

  public void setPushTarget(@NotNull GitRepository repository, @NotNull String sourceBranch,
                            @NotNull String targetRemote, @NotNull String targetBranch) {
    String repositoryPath = repository.getRoot().getPath();
    PushTargetInfo existingInfo = find(myState.PUSH_TARGETS, repository, sourceBranch);
    if (existingInfo != null) {
      myState.PUSH_TARGETS.remove(existingInfo);
    }
    myState.PUSH_TARGETS.add(new PushTargetInfo(repositoryPath, sourceBranch, targetRemote, targetBranch));
  }

  @NotNull
  public DvcsBranchSettings getFavoriteBranchSettings() {
    return myState.FAVORITE_BRANCH_SETTINGS;
  }

  public boolean shouldSetUserNameGlobally() {
    return myState.SET_USER_NAME_GLOBALLY;
  }

  public void setUserNameGlobally(boolean value) {
    myState.SET_USER_NAME_GLOBALLY = value;
  }

  public boolean shouldSwapSidesInCompareBranches() {
    return myState.SWAP_SIDES_IN_COMPARE_BRANCHES;
  }

  public void setSwapSidesInCompareBranches(boolean value) {
    myState.SWAP_SIDES_IN_COMPARE_BRANCHES = value;
  }

  @Tag("push-target-info")
  private static class PushTargetInfo extends DvcsBranchInfo {
    @Attribute(value = "target-remote") public String targetRemoteName;
    @Attribute(value = "target-branch") public String targetBranchName;

    @SuppressWarnings("unused")
    public PushTargetInfo() {
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
