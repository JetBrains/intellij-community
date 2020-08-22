// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.util;

import com.intellij.openapi.components.*;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.github.api.GHRepositoryPath;

/**
 * @author Aleksey Pivovarov
 */
@State(name = "GithubProjectSettings", storages = @Storage(StoragePathMacros.WORKSPACE_FILE), reportStatistic = false)
public class GithubProjectSettings implements PersistentStateComponent<GithubProjectSettings.State> {
  private State myState = new State();

  @Override
  public State getState() {
    return myState;
  }

  @Override
  public void loadState(@NotNull State state) {
    myState = state;
  }

  public static GithubProjectSettings getInstance(@NotNull Project project) {
    return ServiceManager.getService(project, GithubProjectSettings.class);
  }

  public static class State {
    @Nullable public String CREATE_PULL_REQUEST_DEFAULT_BRANCH = null;
    @Nullable public String CREATE_PULL_REQUEST_DEFAULT_REPO_USER = null;
    @Nullable public String CREATE_PULL_REQUEST_DEFAULT_REPO_NAME = null;
  }

  @Nullable
  public String getCreatePullRequestDefaultBranch() {
    return myState.CREATE_PULL_REQUEST_DEFAULT_BRANCH;
  }

  public void setCreatePullRequestDefaultBranch(@NotNull String branch) {
    myState.CREATE_PULL_REQUEST_DEFAULT_BRANCH = branch;
  }

  @Nullable
  public GHRepositoryPath getCreatePullRequestDefaultRepo() {
    if (myState.CREATE_PULL_REQUEST_DEFAULT_REPO_USER == null || myState.CREATE_PULL_REQUEST_DEFAULT_REPO_NAME == null) {
      return null;
    }
    return new GHRepositoryPath(myState.CREATE_PULL_REQUEST_DEFAULT_REPO_USER, myState.CREATE_PULL_REQUEST_DEFAULT_REPO_NAME);
  }

  public void setCreatePullRequestDefaultRepo(@NotNull GHRepositoryPath repo) {
    myState.CREATE_PULL_REQUEST_DEFAULT_REPO_USER = repo.getOwner();
    myState.CREATE_PULL_REQUEST_DEFAULT_REPO_NAME = repo.getRepository();
  }
}
