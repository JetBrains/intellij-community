// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.config;

import com.google.common.collect.ImmutableSet;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.components.StoragePathMacros;
import git4idea.pull.GitPullOption;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.EnumSet;
import java.util.Set;

@State(name = "Git.Pull.Settings", storages = @Storage(StoragePathMacros.WORKSPACE_FILE))
public class GitPullSettings implements PersistentStateComponent<GitPullSettings.State> {

  private State myState = new State();

  public static class State {
    public Set<GitPullOption> OPTIONS = EnumSet.noneOf(GitPullOption.class);
    public String BRANCH = null;
  }

  @Override
  public @NotNull GitPullSettings.State getState() {
    return myState;
  }

  @Override
  public void loadState(@NotNull State state) {
    myState = state;
  }

  public @NotNull Set<GitPullOption> getOptions() {
    return ImmutableSet.copyOf(myState.OPTIONS);
  }

  public void setOptions(@NotNull Set<GitPullOption> options) {
    myState.OPTIONS = EnumSet.copyOf(options);
  }

  public @Nullable String getBranch() {
    return myState.BRANCH;
  }

  public void setBranch(@Nullable String branch) {
    myState.BRANCH = branch;
  }
}
