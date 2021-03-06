// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.util;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.util.ThreeState;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.github.authentication.GithubAuthenticationManager;

import static org.jetbrains.plugins.github.util.GithubAuthData.AuthType;

@State(name = "GithubSettings", storages = {
  @Storage(value = "github.xml"),
  @Storage(value = "github_settings.xml", deprecated = true)
})
public class GithubSettings implements PersistentStateComponent<GithubSettings.State> {
  private State myState = new State();

  @Override
  public State getState() {
    return myState;
  }

  @Override
  public void loadState(@NotNull State state) {
    myState = state;
  }

  public static class State {
    @Nullable public String LOGIN = null;
    @Nullable public String HOST = null;
    @Nullable public AuthType AUTH_TYPE = null;

    public boolean OPEN_IN_BROWSER_GIST = true;
    public boolean COPY_URL_GIST = false;
    // "Secret" in UI, "Public" in API. "Private" here to preserve user settings after refactoring
    public boolean PRIVATE_GIST = true;
    public int CONNECTION_TIMEOUT = 5000;
    public ThreeState CREATE_PULL_REQUEST_CREATE_REMOTE = ThreeState.UNSURE;
    public boolean CLONE_GIT_USING_SSH = false;
  }

  public static GithubSettings getInstance() {
    return ApplicationManager.getApplication().getService(GithubSettings.class);
  }

  public int getConnectionTimeout() {
    return myState.CONNECTION_TIMEOUT;
  }

  public void setConnectionTimeout(int timeout) {
    myState.CONNECTION_TIMEOUT = timeout;
  }

  public boolean isOpenInBrowserGist() {
    return myState.OPEN_IN_BROWSER_GIST;
  }

  public boolean isCopyURLGist() {
    return myState.COPY_URL_GIST;
  }

  public void setCopyURLGist(boolean copyLink) {
    myState.COPY_URL_GIST = copyLink;
  }

  public boolean isPrivateGist() {
    return myState.PRIVATE_GIST;
  }

  public boolean isCloneGitUsingSsh() {
    return myState.CLONE_GIT_USING_SSH;
  }

  @NotNull
  public ThreeState getCreatePullRequestCreateRemote() {
    return myState.CREATE_PULL_REQUEST_CREATE_REMOTE;
  }

  public void setCreatePullRequestCreateRemote(@NotNull ThreeState value) {
    myState.CREATE_PULL_REQUEST_CREATE_REMOTE = value;
  }

  public void setPrivateGist(final boolean secretGist) {
    myState.PRIVATE_GIST = secretGist;
  }

  public void setOpenInBrowserGist(final boolean openInBrowserGist) {
    myState.OPEN_IN_BROWSER_GIST = openInBrowserGist;
  }

  public void setCloneGitUsingSsh(boolean value) {
    myState.CLONE_GIT_USING_SSH = value;
  }

  //region Deprecated auth

  /**
   * @deprecated {@link GithubAuthenticationManager}
   */
  @Deprecated
  @Nullable
  public String getHost() {
    return myState.HOST;
  }

  /**
   * @deprecated {@link GithubAuthenticationManager}
   */
  @Deprecated
  @ApiStatus.ScheduledForRemoval(inVersion = "2021.3")
  @Nullable
  public String getLogin() {
    return myState.LOGIN;
  }

  /**
   * @deprecated {@link GithubAuthenticationManager}
   */
  @Deprecated
  @Nullable
  public AuthType getAuthType() {
    return myState.AUTH_TYPE;
  }

  /**
   * @deprecated {@link GithubAuthenticationManager}
   */
  @Deprecated
  @ApiStatus.ScheduledForRemoval(inVersion = "2021.3")
  public boolean isAuthConfigured() {
    return GithubAuthenticationManager.getInstance().hasAccounts();
  }

  /**
   * @deprecated {@link GithubAuthenticationManager}
   */
  @Deprecated
  @ApiStatus.ScheduledForRemoval(inVersion = "2021.3")
  @NotNull
  public GithubAuthData getAuthData() {
    throw new IllegalStateException("Single account auth is deprecated");
  }

  public void clearAuth() {
    myState.HOST = null;
    myState.LOGIN = null;
    myState.AUTH_TYPE = null;
  }
  //endregion
}