// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.util;

import com.intellij.collaboration.snippets.PathHandlingMode;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.SettingsCategory;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import org.jetbrains.annotations.NotNull;

@State(name = "GithubSettings", storages = {
  @Storage(value = "github.xml"),
  @Storage(value = "github_settings.xml", deprecated = true),
}, category = SettingsCategory.TOOLS)
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
    public boolean OPEN_IN_BROWSER_GIST = true;
    public boolean COPY_URL_GIST = false;
    // "Secret" in UI, "Public" in API. "Private" here to preserve user settings after refactoring
    public boolean PRIVATE_GIST = true;
    public int CONNECTION_TIMEOUT = 5000;
    public boolean CLONE_GIT_USING_SSH = false;
    public boolean AUTOMATICALLY_MARK_AS_VIEWED = true;
    public boolean IS_UNREAD_MARKERS_ENABLED = true;
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

  public boolean isAutomaticallyMarkAsViewed() {
    return myState.AUTOMATICALLY_MARK_AS_VIEWED;
  }

  public boolean isSeenMarkersEnabled() {
    return myState.IS_UNREAD_MARKERS_ENABLED;
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

  public void setAutomaticallyMarkAsViewed(final boolean automaticallyMarkAsViewed) {
    myState.AUTOMATICALLY_MARK_AS_VIEWED = automaticallyMarkAsViewed;
  }

  public void setIsSeenMarkersEnabled(final boolean isUnreadMarkersEnabled) {
    myState.IS_UNREAD_MARKERS_ENABLED = isUnreadMarkersEnabled;
  }
}