/*
 * Copyright 2000-2012 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package git4idea.config;

import com.intellij.lifecycle.PeriodicalTasksCloser;
import com.intellij.openapi.components.*;
import com.intellij.openapi.project.Project;
import com.intellij.util.ArrayUtil;
import git4idea.ui.branch.GitBranchSyncSetting;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Git VCS settings
 */
@State(name = "Git.Settings", roamingType = RoamingType.DISABLED, storages = {@Storage(file = StoragePathMacros.WORKSPACE_FILE)})
public class GitVcsSettings implements PersistentStateComponent<GitVcsSettings.State> {

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
    // The previously entered authors of the commit (up to {@value #PREVIOUS_COMMIT_AUTHORS_LIMIT})
    public List<String> PREVIOUS_COMMIT_AUTHORS = new ArrayList<String>();
    public GitVcsApplicationSettings.SshExecutable SSH_EXECUTABLE = GitVcsApplicationSettings.SshExecutable.IDEA_SSH;
    // The policy that specifies how files are saved before update or rebase
    public UpdateChangesPolicy UPDATE_CHANGES_POLICY = UpdateChangesPolicy.STASH;
    public UpdateMethod UPDATE_TYPE = UpdateMethod.BRANCH_DEFAULT;
    public boolean PUSH_AUTO_UPDATE = false;
    public GitBranchSyncSetting SYNC_SETTING = GitBranchSyncSetting.NOT_DECIDED;
    public String RECENT_GIT_ROOT_PATH = null;
    public Map<String, String> RECENT_BRANCH_BY_REPOSITORY = new HashMap<String, String>();
    public String RECENT_COMMON_BRANCH = null;
    public boolean AUTO_COMMIT_ON_CHERRY_PICK = false;
    public boolean WARN_ABOUT_CRLF = true;
  }

  public GitVcsSettings(GitVcsApplicationSettings appSettings) {
    myAppSettings = appSettings;
  }

  public GitVcsApplicationSettings getAppSettings() {
    return myAppSettings;
  }
  
  public static GitVcsSettings getInstance(Project project) {
    return PeriodicalTasksCloser.getInstance().safeGetService(project, GitVcsSettings.class);
  }

  public UpdateMethod getUpdateType() {
    return myState.UPDATE_TYPE;
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

  public boolean autoUpdateIfPushRejected() {
    return myState.PUSH_AUTO_UPDATE;
  }

  public void setAutoUpdateIfPushRejected(boolean autoUpdate) {
    myState.PUSH_AUTO_UPDATE = autoUpdate;
  }

  @NotNull
  public GitBranchSyncSetting getSyncSetting() {
    return myState.SYNC_SETTING;
  }

  public void setSyncSetting(@NotNull GitBranchSyncSetting syncSetting) {
    myState.SYNC_SETTING = syncSetting;
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

  public boolean warnAboutCrlf() {
    return myState.WARN_ABOUT_CRLF;
  }

  public void setWarnAboutCrlf(boolean warn) {
    myState.WARN_ABOUT_CRLF = warn;
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

}
