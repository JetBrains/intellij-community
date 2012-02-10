/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
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
@State(name = "Git.Settings", storages = {@Storage(file = "$WORKSPACE_FILE$")})
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

  /**
   * Kinds of SSH executable to be used with the git
   */
  public enum SshExecutable {
    IDEA_SSH,
    NATIVE_SSH,
  }

  public enum ConversionPolicy {
    NONE, // No conversion is performed
    CONVERT, // The files are converted to project line separators
    ASK  // Show dialog and ask user what to do: convert files or leave unchanged.
  }

  public static class State {
    public List<String> PREVIOUS_COMMIT_AUTHORS = new ArrayList<String>(); // The previously entered authors of the commit (up to {@value #PREVIOUS_COMMIT_AUTHORS_LIMIT})
    public SshExecutable SSH_EXECUTABLE = SshExecutable.IDEA_SSH;
    public UpdateChangesPolicy UPDATE_CHANGES_POLICY = UpdateChangesPolicy.STASH; // The policy that specifies how files are saved before update or rebase
    public UpdateMethod UPDATE_TYPE = UpdateMethod.BRANCH_DEFAULT;
    public ConversionPolicy LINE_SEPARATORS_CONVERSION = ConversionPolicy.CONVERT;
    public boolean PUSH_AUTO_UPDATE = false;
    public GitBranchSyncSetting SYNC_SETTING = GitBranchSyncSetting.NOT_DECIDED;
    public String RECENT_GIT_ROOT_PATH = null;
    public Map<String, String> RECENT_BRANCH_BY_REPOSITORY = new HashMap<String, String>();
    public String RECENT_COMMON_BRANCH = null;
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

  public ConversionPolicy getLineSeparatorsConversion() {
    return myState.LINE_SEPARATORS_CONVERSION;
  }

  public void setLineSeparatorsConversion(ConversionPolicy lineSeparatorsConversion) {
    myState.LINE_SEPARATORS_CONVERSION = lineSeparatorsConversion;
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

  public boolean isIdeaSsh() {
    return myState.SSH_EXECUTABLE == SshExecutable.IDEA_SSH;
  }

  public void setIdeaSsh(boolean value) {
    myState.SSH_EXECUTABLE = value ? SshExecutable.IDEA_SSH : SshExecutable.NATIVE_SSH;
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

}
