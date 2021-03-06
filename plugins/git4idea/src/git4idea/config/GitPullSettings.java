// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.config;

import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.components.StoragePathMacros;
import git4idea.pull.GitPullOption;
import org.jetbrains.annotations.NotNull;

import java.util.EnumSet;
import java.util.Set;

import static java.util.EnumSet.copyOf;

@State(name = "Git.Pull.Settings", storages = @Storage(StoragePathMacros.WORKSPACE_FILE))
public class GitPullSettings implements PersistentStateComponent<GitPullSettings.State> {

  public static class State {
    public @NotNull Set<GitPullOption> OPTIONS = none();
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

  public @NotNull Set<GitPullOption> getOptions() {
    return copyOf(myState.OPTIONS);
  }

  public void setOptions(@NotNull Set<GitPullOption> options) {
    myState.OPTIONS = !options.isEmpty() ? copyOf(options) : none();
  }

  private static @NotNull Set<GitPullOption> none() {
    return EnumSet.noneOf(GitPullOption.class);
  }
}
