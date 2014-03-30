package com.intellij.vcs.log.impl;

import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.components.StoragePathMacros;
import com.intellij.vcs.log.VcsLogSettings;
import org.jetbrains.annotations.Nullable;

/**
 * @author Kirill Likhodedov
 */
@State(name = "Vcs.Log.Settings", storages = {@Storage(file = StoragePathMacros.WORKSPACE_FILE)})
public class VcsLogSettingsImpl implements VcsLogSettings, PersistentStateComponent<VcsLogSettingsImpl.State> {

  private State myState = new State();

  public static class State {
    public int RECENT_COMMITS_COUNT = 1000;
    public boolean SHOW_BRANCHES_PANEL = false;
  }

  @Nullable
  @Override
  public State getState() {
    return myState;
  }

  @Override
  public void loadState(State state) {
    myState = state;
  }

  @Override
  public int getRecentCommitsCount() {
    return myState.RECENT_COMMITS_COUNT;
  }

  @Override
  public boolean isShowBranchesPanel() {
    return myState.SHOW_BRANCHES_PANEL;
  }

  @Override
  public void setShowBranchesPanel(boolean show) {
    myState.SHOW_BRANCHES_PANEL = show;
  }

  public void setRecentCommitsBlockSize(int commitCount) {
    myState.RECENT_COMMITS_COUNT = commitCount;
  }

}
