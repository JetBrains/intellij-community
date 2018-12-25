// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.config;

import com.intellij.openapi.components.*;
import com.intellij.openapi.util.registry.Registry;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * The application wide settings for the git
 */
@State(name = "Git.Application.Settings", storages = @Storage(value = "git.xml", roamingType = RoamingType.PER_OS))
public class GitVcsApplicationSettings implements PersistentStateComponent<GitVcsApplicationSettings.State> {
  private State myState = new State();

  public static class State {
    public String myPathToGit = null;

    public boolean ANNOTATE_IGNORE_SPACES = true;
    public AnnotateDetectMovementsOption ANNOTATE_DETECT_INNER_MOVEMENTS = AnnotateDetectMovementsOption.NONE;
  }

  public static GitVcsApplicationSettings getInstance() {
    return ServiceManager.getService(GitVcsApplicationSettings.class);
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

  public boolean isUseIdeaSsh() {
    return Registry.is("git.use.builtin.ssh");
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

  public enum AnnotateDetectMovementsOption {
    NONE,
    INNER,
    OUTER
  }
}
