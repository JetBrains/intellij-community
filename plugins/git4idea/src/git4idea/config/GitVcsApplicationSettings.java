// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.config;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.*;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.registry.Registry;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * The application wide settings for the git
 */
@State(name = "Git.Application.Settings", storages = @Storage(value = "git.xml", roamingType = RoamingType.PER_OS))
public final class GitVcsApplicationSettings implements PersistentStateComponent<GitVcsApplicationSettings.State> {
  private State myState = new State();

  public static final class State {
    public String myPathToGit = null;

    public boolean ANNOTATE_IGNORE_SPACES = true;
    public AnnotateDetectMovementsOption ANNOTATE_DETECT_INNER_MOVEMENTS = AnnotateDetectMovementsOption.NONE;
    public boolean AUTO_COMMIT_ON_CHERRY_PICK = true;
    public boolean USE_CREDENTIAL_HELPER = false;
    public boolean STAGING_AREA_ENABLED = false;
  }

  public static GitVcsApplicationSettings getInstance() {
    return ApplicationManager.getApplication().getService(GitVcsApplicationSettings.class);
  }

  @Override
  public State getState() {
    return myState;
  }

  @Override
  public void loadState(@NotNull State state) {
    myState = state;
  }

  /**
   * @deprecated use {@link #getSavedPathToGit()} to get the path from settings if there's any
   * or use {@link GitExecutableManager#getPathToGit()}/{@link GitExecutableManager#getPathToGit(Project)} to get git executable with
   * auto-detection
   */
  @NotNull
  @Deprecated
  @ApiStatus.ScheduledForRemoval(inVersion = "2021.3")
  public String getPathToGit() {
    return GitExecutableManager.getInstance().getPathToGit();
  }

  @Nullable
  public String getSavedPathToGit() {
    return myState.myPathToGit;
  }

  public void setPathToGit(@Nullable String pathToGit) {
    myState.myPathToGit = pathToGit;
  }

  public boolean isIgnoreWhitespaces() {
    return myState.ANNOTATE_IGNORE_SPACES;
  }

  public void setIgnoreWhitespaces(boolean value) {
    myState.ANNOTATE_IGNORE_SPACES = value;
  }

  @NotNull
  public AnnotateDetectMovementsOption getAnnotateDetectMovementsOption() {
    return myState.ANNOTATE_DETECT_INNER_MOVEMENTS;
  }

  public void setAnnotateDetectMovementsOption(@NotNull AnnotateDetectMovementsOption value) {
    myState.ANNOTATE_DETECT_INNER_MOVEMENTS = value;
  }

  public void setAutoCommitOnCherryPick(boolean autoCommit) {
    myState.AUTO_COMMIT_ON_CHERRY_PICK = autoCommit;
  }

  public boolean isAutoCommitOnCherryPick() {
    return myState.AUTO_COMMIT_ON_CHERRY_PICK;
  }

  public void setUseCredentialHelper(boolean useCredentialHelper) {
    myState.USE_CREDENTIAL_HELPER = useCredentialHelper;
  }

  public boolean isUseCredentialHelper() {
    return myState.USE_CREDENTIAL_HELPER;
  }

  public boolean isStagingAreaEnabled() {
    if (Registry.is("git.enable.stage")) {
      myState.STAGING_AREA_ENABLED = true;
      Registry.get("git.enable.stage").setValue(false);
    }
    return myState.STAGING_AREA_ENABLED;
  }

  public void setStagingAreaEnabled(boolean isStagingAreaEnabled) {
    myState.STAGING_AREA_ENABLED = isStagingAreaEnabled;
  }

  public enum AnnotateDetectMovementsOption {
    NONE,
    INNER,
    OUTER
  }
}
