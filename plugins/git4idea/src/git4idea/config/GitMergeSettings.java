// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.config;

import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.components.StoragePathMacros;
import git4idea.merge.GitMergeOption;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.EnumSet;
import java.util.Set;

import static java.util.EnumSet.copyOf;

@State(name = "Git.Merge.Settings", storages = @Storage(StoragePathMacros.WORKSPACE_FILE))
public class GitMergeSettings implements PersistentStateComponent<GitMergeSettings.State> {

  public static class State {
    public @Nullable String BRANCH = null;
    public @NotNull Set<GitMergeOption> OPTIONS = none();
  }

  private State myState = new State();

  @Override
  public @NotNull State getState() {
    return myState;
  }

  @Override
  public void loadState(@NotNull State state) {
    myState = state;
  }

  public @Nullable String getBranch() {
    return myState.BRANCH;
  }

  public void setBranch(@Nullable String branch) {
    myState.BRANCH = branch;
  }

  public @NotNull Set<GitMergeOption> getOptions() {
    return copyOf(myState.OPTIONS);
  }

  public void setOptions(@NotNull Set<GitMergeOption> options) {
    myState.OPTIONS = !options.isEmpty() ? copyOf(options) : none();
  }

  private static @NotNull Set<GitMergeOption> none() {
    return EnumSet.noneOf(GitMergeOption.class);
  }
}
