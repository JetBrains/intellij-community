// Copyright 2008-2010 Victor Iacoban
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
// http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software distributed under
// the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
// either express or implied. See the License for the specific language governing permissions and
// limitations under the License.
package org.zmlx.hg4idea;

import com.intellij.dvcs.branch.DvcsBranchSettings;
import com.intellij.dvcs.branch.DvcsSyncSettings;
import com.intellij.lifecycle.PeriodicalTasksCloser;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.components.StoragePathMacros;
import com.intellij.openapi.progress.util.BackgroundTaskUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.changes.VcsAnnotationRefresher;
import com.intellij.util.xmlb.annotations.Property;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@State(
  name = "hg4idea.settings",
  storages = @Storage(StoragePathMacros.WORKSPACE_FILE)
)
public class HgProjectSettings implements PersistentStateComponent<HgProjectSettings.State>, DvcsSyncSettings {

  @NotNull private final HgGlobalSettings myAppSettings;
  @NotNull private final Project myProject;

  private State myState = new State();

  public HgProjectSettings(@NotNull Project project, @NotNull HgGlobalSettings appSettings) {
    myProject = project;
    myAppSettings = appSettings;
  }

  public static class State {

    public boolean myCheckIncoming = true;
    public boolean myCheckOutgoing = true;
    public Boolean CHECK_INCOMING_OUTGOING = null;
    public boolean myIgnoreWhitespacesInAnnotations = true;
    public String RECENT_HG_ROOT_PATH = null;
    public Value ROOT_SYNC = Value.NOT_DECIDED;
    
    @Property(surroundWithTag = false, flat = true)
    public DvcsBranchSettings FAVORITE_BRANCH_SETTINGS = new DvcsBranchSettings();
  }

  public State getState() {
    return myState;
  }

  public void loadState(State state) {
    myState = state;
    if (state.CHECK_INCOMING_OUTGOING == null) {
      state.CHECK_INCOMING_OUTGOING = state.myCheckIncoming || state.myCheckOutgoing;
    }
  }

  public static HgProjectSettings getInstance(@NotNull Project project) {
    return PeriodicalTasksCloser.getInstance().safeGetService(project, HgProjectSettings.class);
  }

  @Nullable
  public String getRecentRootPath() {
    return myState.RECENT_HG_ROOT_PATH;
  }

  public void setRecentRootPath(@NotNull String recentRootPath) {
    myState.RECENT_HG_ROOT_PATH = recentRootPath;
  }

  public boolean isCheckIncomingOutgoing() {
    if (myState.CHECK_INCOMING_OUTGOING == null) {
      return myState.myCheckIncoming || myState.myCheckOutgoing;
    }
    return myState.CHECK_INCOMING_OUTGOING.booleanValue();
  }

  public boolean isWhitespacesIgnoredInAnnotations() {
    return myState.myIgnoreWhitespacesInAnnotations;
  }

  @NotNull
  public Value getSyncSetting() {
    return myState.ROOT_SYNC;
  }

  public void setSyncSetting(@NotNull Value syncSetting) {
    myState.ROOT_SYNC = syncSetting;
  }

  public void setCheckIncomingOutgoing(boolean checkIncomingOutgoing) {
    myState.CHECK_INCOMING_OUTGOING = checkIncomingOutgoing;
  }

  public void setIgnoreWhitespacesInAnnotations(boolean ignoreWhitespacesInAnnotations) {
    if (myState.myIgnoreWhitespacesInAnnotations != ignoreWhitespacesInAnnotations) {
      myState.myIgnoreWhitespacesInAnnotations = ignoreWhitespacesInAnnotations;
      BackgroundTaskUtil.syncPublisher(myProject, VcsAnnotationRefresher.LOCAL_CHANGES_CHANGED).configurationChanged(HgVcs.getKey());
    }
  }

  @NotNull
  public DvcsBranchSettings getFavoriteBranchSettings() {
    return myState.FAVORITE_BRANCH_SETTINGS;
  }

  public String getHgExecutable() {
    return myAppSettings.getHgExecutable();
  }

  public void setHgExecutable(String text) {
    myAppSettings.setHgExecutable(text);
  }

  @NotNull
  public HgGlobalSettings getGlobalSettings() {
    return myAppSettings;
  }
}
