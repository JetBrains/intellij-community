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
import com.intellij.openapi.components.*;
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

  @NotNull private final Project myProject;

  private State myState = new State();

  public HgProjectSettings(@NotNull Project project) {
    myProject = project;
  }

  public static class State {

    public String PATH_TO_EXECUTABLE = null;
    public boolean OVERRIDE_APPLICATION_PATH_TO_EXECUTABLE = false;
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
    return ServiceManager.getService(project, HgProjectSettings.class);
  }

  @Nullable
  public String getHgExecutable() {
    return myState.PATH_TO_EXECUTABLE;
  }

  public void setHgExecutable(@Nullable String path) {
    myState.PATH_TO_EXECUTABLE = path;
  }

  public boolean isHgExecutableOverridden() {
    return myState.OVERRIDE_APPLICATION_PATH_TO_EXECUTABLE;
  }

  public void setHgExecutableOverridden(boolean overridden) {
    myState.OVERRIDE_APPLICATION_PATH_TO_EXECUTABLE = overridden;
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

  public void setCheckIncomingOutgoing(boolean checkIncomingOutgoing) {
    myState.CHECK_INCOMING_OUTGOING = checkIncomingOutgoing;
  }

  @NotNull
  public Value getSyncSetting() {
    return myState.ROOT_SYNC;
  }

  public void setSyncSetting(@NotNull Value syncSetting) {
    myState.ROOT_SYNC = syncSetting;
  }

  public boolean isWhitespacesIgnoredInAnnotations() {
    return myState.myIgnoreWhitespacesInAnnotations;
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
}
