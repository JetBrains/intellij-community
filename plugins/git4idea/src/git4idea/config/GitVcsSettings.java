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
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Git VCS settings
 */
@State(
  name = "Git.Settings",
  storages = {@Storage(
    id = "ws",
    file = "$WORKSPACE_FILE$")})
public class GitVcsSettings implements PersistentStateComponent<GitVcsSettings.State> {

  public static final int PREVIOUS_COMMIT_AUTHORS_LIMIT = 16; // Limit for previous commit authors
  private static final SshExecutable DEFAULT_SSH = SshExecutable.IDEA_SSH; // Default SSH policy

  private final GitVcsApplicationSettings myAppSettings;
  private final List<String> myCommitAuthors = new ArrayList<String>(); // The previously entered authors of the commit (up to {@value #PREVIOUS_COMMIT_AUTHORS_LIMIT})
  private boolean myCheckoutIncludesTags = false;
  private SshExecutable mySshExecutable = DEFAULT_SSH; // IDEA SSH should be used instead of native SSH.
  private UpdateChangesPolicy myUpdateChangesPolicy = UpdateChangesPolicy.STASH; // The policy that specifies how files are saved before update or rebase
  private UpdateType myUpdateType = UpdateType.BRANCH_DEFAULT; // The type of update operation to perform
  private ConversionPolicy myLineSeparatorsConversion = ConversionPolicy.CONVERT; // The crlf conversion policy
  private UpdateChangesPolicy myPushActiveBranchesRebaseSavePolicy = UpdateChangesPolicy.STASH; // The policy used in push active branches dialog

  public GitVcsSettings(GitVcsApplicationSettings appSettings) {
    myAppSettings = appSettings;
  }

  public GitVcsApplicationSettings getAppSettings() {
    return myAppSettings;
  }

  /**
   * @return save policy for push active branches dialog
   */
  public UpdateChangesPolicy getPushActiveBranchesRebaseSavePolicy() {
    return myPushActiveBranchesRebaseSavePolicy;
  }

  /**
   * Change save policy for push active branches dialog
   *
   * @param pushActiveBranchesRebaseSavePolicy
   *         the new policy value
   */
  public void setPushActiveBranchesRebaseSavePolicy(UpdateChangesPolicy pushActiveBranchesRebaseSavePolicy) {
    myPushActiveBranchesRebaseSavePolicy = pushActiveBranchesRebaseSavePolicy;
  }

  /**
   * @return policy for converting line separators
   */
  public ConversionPolicy getLineSeparatorsConversion() {
    return myLineSeparatorsConversion;
  }

  /**
   * Modify line separators policy
   *
   * @param lineSeparatorsConversion the new policy value
   */
  public void setLineSeparatorsConversion(ConversionPolicy lineSeparatorsConversion) {
    myLineSeparatorsConversion = lineSeparatorsConversion;
  }

  /**
   * @return update type
   */
  public UpdateType getUpdateType() {
    return myUpdateType;
  }

  /**
   * Set update type
   *
   * @param updateType the update type to set
   */
  public void setUpdateType(UpdateType updateType) {
    myUpdateType = updateType;
  }

  /**
   * @return true if drop down in checkout dialog includes tags
   */
  public boolean isCheckoutIncludesTags() {
    return myCheckoutIncludesTags;
  }

  /**
   * Record whether checkout dialog option included tags last time
   *
   * @param value the value to record
   */
  public void setCheckoutIncludesTags(boolean value) {
    myCheckoutIncludesTags = value;
  }

  /**
   * @return get (a possibly converted value) of update stash policy
   */
  @NotNull
  public UpdateChangesPolicy updateChangesPolicy() {
    return myUpdateChangesPolicy;
  }

  /**
   * Save update changes policy
   *
   * @param value the value to save
   */
  public void setUpdateChangesPolicy(UpdateChangesPolicy value) {
    myUpdateChangesPolicy = value;
  }

  /**
   * Save an author of the commit and make it the first one. If amount of authors exceeds the limit, remove least recently selected author.
   *
   * @param author an author to save
   */
  public void saveCommitAuthor(String author) {
    myCommitAuthors.remove(author);
    while (myCommitAuthors.size() >= PREVIOUS_COMMIT_AUTHORS_LIMIT) {
      myCommitAuthors.remove(myCommitAuthors.size() - 1);
    }
    myCommitAuthors.add(0, author);
  }

  /**
   * @return array for commit authors
   */
  public String[] getCommitAuthors() {
    return ArrayUtil.toStringArray(myCommitAuthors);
  }

  public State getState() {
    State s = new State();
    s.CHECKOUT_INCLUDE_TAGS = myCheckoutIncludesTags;
    s.LINE_SEPARATORS_CONVERSION = myLineSeparatorsConversion;
    s.PREVIOUS_COMMIT_AUTHORS = getCommitAuthors();
    s.PUSH_ACTIVE_BRANCHES_REBASE_SAVE_POLICY = myPushActiveBranchesRebaseSavePolicy;
    s.SSH_EXECUTABLE = mySshExecutable;
    s.UPDATE_CHANGES_POLICY = myUpdateChangesPolicy;
    s.UPDATE_STASH = true;
    s.UPDATE_TYPE = myUpdateType;
    return s;
  }

  public void loadState(State s) {
    myCheckoutIncludesTags = s.CHECKOUT_INCLUDE_TAGS == null ? false : s.CHECKOUT_INCLUDE_TAGS;
    myLineSeparatorsConversion = s.LINE_SEPARATORS_CONVERSION;
    myCommitAuthors.clear();
    ContainerUtil.addAll(myCommitAuthors, s.PREVIOUS_COMMIT_AUTHORS);
    myPushActiveBranchesRebaseSavePolicy = s.PUSH_ACTIVE_BRANCHES_REBASE_SAVE_POLICY;
    mySshExecutable = s.SSH_EXECUTABLE;
    myUpdateChangesPolicy = s.UPDATE_CHANGES_POLICY;
    if (myUpdateChangesPolicy == null) {
      myUpdateChangesPolicy = s.UPDATE_STASH ? UpdateChangesPolicy.STASH : UpdateChangesPolicy.KEEP;
    }
    myUpdateType = s.UPDATE_TYPE;
  }

  /**
   * Get git setting for the project
   *
   * @param project a context project
   * @return the git settings
   */
  @Nullable
  public static GitVcsSettings getInstance(Project project) {
    if (project == null || project.isDisposed()) {
      return null;
    }
    return PeriodicalTasksCloser.getInstance().safeGetService(project, GitVcsSettings.class);
  }

  /**
   * @return true if IDEA ssh should be used
   */
  public boolean isIdeaSsh() {
    return (mySshExecutable == null ? DEFAULT_SSH : mySshExecutable) == SshExecutable.IDEA_SSH;
  }

  /**
   * @return true if IDEA ssh should be used
   */
  public static boolean isDefaultIdeaSsh() {
    return DEFAULT_SSH == SshExecutable.IDEA_SSH;
  }

  /**
   * Set IDEA ssh value
   *
   * @param value the value to set
   */
  public void setIdeaSsh(boolean value) {
    mySshExecutable = value ? SshExecutable.IDEA_SSH : SshExecutable.NATIVE_SSH;
  }

  /**
   * The state fo the settings
   */
  public static class State {

    /**
     * The previously entered authors of the commit (up to {@value #PREVIOUS_COMMIT_AUTHORS_LIMIT})
     */
    public String[] PREVIOUS_COMMIT_AUTHORS = {};
    /**
     * Checkout includes tags
     */
    public Boolean CHECKOUT_INCLUDE_TAGS;
    /**
     * IDEA SSH should be used instead of native SSH.
     */
    public SshExecutable SSH_EXECUTABLE = DEFAULT_SSH;
    /**
     * True if stash/unstash operation should be performed before update (Obsolete option)
     */
    public boolean UPDATE_STASH = true;
    /**
     * The policy that specifies how files are saved before update or rebase
     */
    public UpdateChangesPolicy UPDATE_CHANGES_POLICY = null;
    /**
     * The type of update operation to perform
     */
    public UpdateType UPDATE_TYPE = UpdateType.BRANCH_DEFAULT;
    /**
     * The crlf conversion policy
     */
    public ConversionPolicy LINE_SEPARATORS_CONVERSION = ConversionPolicy.CONVERT;
    /**
     * If true, the dialog is shown with conversion options
     */
    public boolean LINE_SEPARATORS_CONVERSION_ASK = true;
    /**
     * The policy used in push active branches dialog
     */
    public UpdateChangesPolicy PUSH_ACTIVE_BRANCHES_REBASE_SAVE_POLICY = UpdateChangesPolicy.STASH;
  }

  /**
   * The way the local changes are saved before update if user has selected auto-stash
   */
  public enum UpdateChangesPolicy {
    /**
     * Stash changes
     */
    STASH,
    /**
     * Shelve changes
     */
    SHELVE,
    /**
     * Keep files in working tree
     */
    KEEP
  }

  /**
   * Kinds of SSH executable to be used with the git
   */
  public enum SshExecutable {
    /**
     * SSH provided by IDEA
     */
    IDEA_SSH,
    /**
     * Naive SSH.
     */
    NATIVE_SSH,
  }

  /**
   * The type of update to perform
   */
  public enum UpdateType {
    /**
     * Use default specified in the config file for the branch
     */
    BRANCH_DEFAULT,
    /**
     * Merge fetched commits with local branch
     */
    MERGE,
    /**
     * Rebase local commits upon the fetched branch
     */
    REBASE
  }

  /**
   * The CRLF conversion policy
   */
  public enum ConversionPolicy {
    /**
     * No conversion is performed
     */
    NONE,
    /**
     * The files are converted to project line separators
     */
    CONVERT,
    /**
     * Show dialog and ask user what to do: convert files or leave unchanged.
     */
    ASK
  }
}
