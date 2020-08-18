// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.config;

import com.google.common.collect.ImmutableSet;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.components.StoragePathMacros;
import git4idea.rebase.RebaseOption;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.EnumSet;
import java.util.Set;

@State(name = "Git.Rebase.Settings", storages = @Storage(StoragePathMacros.WORKSPACE_FILE))
public class GitRebaseSettings implements PersistentStateComponent<GitRebaseSettings.State> {

  private final static Set<RebaseOption> NO_OPTIONS = EnumSet.noneOf(RebaseOption.class);

  private State myState = new State();

  public static class State {
    public Set<RebaseOption> OPTIONS = NO_OPTIONS;
    public String NEW_BASE = null;
  }

  @Nullable
  @Override
  public State getState() {
    return myState;
  }

  @Override
  public void loadState(@NotNull State state) {
    myState = state;
  }

  @NotNull
  public Set<RebaseOption> getOptions() {
    return ImmutableSet.copyOf(myState.OPTIONS);
  }

  public void setOptions(@NotNull Set<RebaseOption> options) {
    myState.OPTIONS = !options.isEmpty()
                      ? EnumSet.copyOf(options)
                      : NO_OPTIONS;
  }

  @Nullable
  public String getNewBase() {
    return myState.NEW_BASE;
  }

  public void setNewBase(@Nullable String newBase) {
    myState.NEW_BASE = newBase;
  }
}
