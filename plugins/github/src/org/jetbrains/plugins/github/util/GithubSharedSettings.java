// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.util;

import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.project.Project;
import git4idea.config.GitSharedSettings;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@State(name = "GithubSharedSettings", storages = @Storage("vcs.xml"))
public class GithubSharedSettings implements PersistentStateComponent<GithubSharedSettings.State> {

  public static class State {
    public boolean MERGE_ACTIONS_ALLOWED = true;
  }

  private State myState = new State();

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
  public static GitSharedSettings getInstance(@NotNull Project project) {
    return ServiceManager.getService(project, GitSharedSettings.class);
  }

  public boolean areMergeActionsAllowed() {
    return myState.MERGE_ACTIONS_ALLOWED;
  }
}
