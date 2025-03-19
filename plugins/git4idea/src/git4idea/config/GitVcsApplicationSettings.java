// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.config;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.*;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * The application wide settings for the git
 */
@State(name = "Git.Application.Settings",
  category = SettingsCategory.TOOLS,
  exportable = true,
  storages = @Storage(value = "git.xml", roamingType = RoamingType.DISABLED))
public final class GitVcsApplicationSettings implements PersistentStateComponent<GitVcsApplicationSettings.State> {
  private State myState = new State();

  public static final class State {
    public String myPathToGit = null;

    public boolean ANNOTATE_IGNORE_SPACES = true;
    public AnnotateDetectMovementsOption ANNOTATE_DETECT_INNER_MOVEMENTS = AnnotateDetectMovementsOption.NONE;
    public boolean USE_CREDENTIAL_HELPER = false;
    public boolean STAGING_AREA_ENABLED = false;
    public boolean COMBINED_STASHES_AND_SHELVES_ENABLED = false;
    public boolean COMPARE_WITH_LOCAL_IN_STASHES_ENABLED = true;
    public boolean SPLIT_DIFF_PREVIEW_IN_STASHES_ENABLED = true;

    public boolean SHOW_DROP_COMMIT_DIALOG = true;
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
  @Deprecated(forRemoval = true)
  public @NotNull String getPathToGit() {
    return GitExecutableManager.getInstance().getPathToGit();
  }

  public @Nullable String getSavedPathToGit() {
    return myState.myPathToGit;
  }

  public void setPathToGit(@Nullable String pathToGit) {
    myState.myPathToGit = pathToGit;
    GitExecutableDetector.fireExecutableChanged();
  }

  public boolean isIgnoreWhitespaces() {
    return myState.ANNOTATE_IGNORE_SPACES;
  }

  public void setIgnoreWhitespaces(boolean value) {
    myState.ANNOTATE_IGNORE_SPACES = value;
  }

  public @NotNull AnnotateDetectMovementsOption getAnnotateDetectMovementsOption() {
    return myState.ANNOTATE_DETECT_INNER_MOVEMENTS;
  }

  public void setAnnotateDetectMovementsOption(@NotNull AnnotateDetectMovementsOption value) {
    myState.ANNOTATE_DETECT_INNER_MOVEMENTS = value;
  }

  public void setUseCredentialHelper(boolean useCredentialHelper) {
    myState.USE_CREDENTIAL_HELPER = useCredentialHelper;
  }

  public boolean isUseCredentialHelper() {
    return myState.USE_CREDENTIAL_HELPER;
  }

  public boolean isStagingAreaEnabled() {
    return myState.STAGING_AREA_ENABLED;
  }

  public void setStagingAreaEnabled(boolean isStagingAreaEnabled) {
    myState.STAGING_AREA_ENABLED = isStagingAreaEnabled;
  }

  public boolean isCombinedStashesAndShelvesTabEnabled() {
    return myState.COMBINED_STASHES_AND_SHELVES_ENABLED;
  }

  public void setCombinedStashesAndShelvesTabEnabled(boolean isEnabled) {
    myState.COMBINED_STASHES_AND_SHELVES_ENABLED = isEnabled;
  }

  public boolean isCompareWithLocalInStashesEnabled() {
    return myState.COMPARE_WITH_LOCAL_IN_STASHES_ENABLED;
  }

  public void setCompareWithLocalInStashesEnabled(boolean isEnabled) {
    myState.COMPARE_WITH_LOCAL_IN_STASHES_ENABLED = isEnabled;
  }

  public boolean isSplitDiffPreviewInStashesEnabled() {
    return myState.SPLIT_DIFF_PREVIEW_IN_STASHES_ENABLED;
  }

  public void setSplitDiffPreviewInStashesEnabled(boolean isEnabled) {
    myState.SPLIT_DIFF_PREVIEW_IN_STASHES_ENABLED = isEnabled;
  }

  public boolean isShowDropCommitDialog() {
    return myState.SHOW_DROP_COMMIT_DIALOG;
  }

  public void setShowDropCommitDialog(boolean isShowDropCommitDialog) {
    myState.SHOW_DROP_COMMIT_DIALOG = isShowDropCommitDialog;
  }

  public enum AnnotateDetectMovementsOption {
    NONE,
    INNER,
    OUTER
  }
}
