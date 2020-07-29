// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.config;

import com.google.common.collect.ImmutableSet;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.components.StoragePathMacros;
import git4idea.merge.MergeOption;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.EnumSet;
import java.util.Set;

@State(name = "Git.Merge.Settings", storages = @Storage(StoragePathMacros.WORKSPACE_FILE))
public class GitMergeSettings implements PersistentStateComponent<GitMergeSettings.State> {

  private final static Set<MergeOption> NO_OPTIONS = EnumSet.noneOf(MergeOption.class);

  private GitMergeSettings.State myState = new GitMergeSettings.State();

  public static class State {
    public Set<MergeOption> OPTIONS = NO_OPTIONS;
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
  public Set<MergeOption> getOptions() {
    return ImmutableSet.copyOf(myState.OPTIONS);
  }

  public void setOptions(@NotNull Set<MergeOption> options) {
    myState.OPTIONS = !options.isEmpty()
                      ? EnumSet.copyOf(options)
                      : NO_OPTIONS;
  }
}
