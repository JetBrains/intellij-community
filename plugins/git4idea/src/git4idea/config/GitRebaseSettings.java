// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.config;

import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.components.StoragePathMacros;
import git4idea.rebase.GitRebaseOption;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.EnumSet;
import java.util.Set;

import static java.util.EnumSet.copyOf;

@State(name = "Git.Rebase.Settings", storages = @Storage(StoragePathMacros.WORKSPACE_FILE))
public class GitRebaseSettings implements PersistentStateComponent<GitRebaseSettings.State> {

  public static class State {
    public @Nullable String NEW_BASE = null;
    public @NotNull Set<GitRebaseOption> OPTIONS = none();
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

  public @Nullable String getNewBase() {
    return myState.NEW_BASE;
  }

  public void setNewBase(@Nullable String newBase) {
    myState.NEW_BASE = newBase;
  }

  public @NotNull Set<GitRebaseOption> getOptions() {
    return copyOf(myState.OPTIONS);
  }

  public void setOptions(@NotNull Set<GitRebaseOption> options) {
    myState.OPTIONS = !options.isEmpty() ? copyOf(options) : none();
  }

  private static @NotNull Set<GitRebaseOption> none() {
    return EnumSet.noneOf(GitRebaseOption.class);
  }
}
