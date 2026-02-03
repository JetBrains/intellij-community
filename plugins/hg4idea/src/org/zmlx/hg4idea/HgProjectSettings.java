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
import com.intellij.dvcs.branch.DvcsCompareSettings;
import com.intellij.dvcs.branch.DvcsSyncSettings;
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
public class HgProjectSettings implements PersistentStateComponent<HgProjectSettings.State>, DvcsSyncSettings, DvcsCompareSettings {

  private final @NotNull Project myProject;

  private State myState = new State();

  public HgProjectSettings(@NotNull Project project) {
    myProject = project;
  }

  public static class State {

    public String PATH_TO_EXECUTABLE = null;
    public boolean OVERRIDE_APPLICATION_PATH_TO_EXECUTABLE = false;
    public boolean CHECK_INCOMING_OUTGOING = false;
    public boolean myIgnoreWhitespacesInAnnotations = true;
    public String RECENT_HG_ROOT_PATH = null;
    public Value ROOT_SYNC = Value.NOT_DECIDED;
    public boolean SWAP_SIDES_IN_COMPARE_BRANCHES = false;

    @Property(surroundWithTag = false, flat = true)
    public DvcsBranchSettings BRANCH_SETTINGS = new DvcsBranchSettings();
  }

  @Override
  public State getState() {
    return myState;
  }

  @Override
  public void loadState(@NotNull State state) {
    myState = state;
  }

  public static HgProjectSettings getInstance(@NotNull Project project) {
    return project.getService(HgProjectSettings.class);
  }

  public @Nullable String getHgExecutable() {
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

  public @Nullable String getRecentRootPath() {
    return myState.RECENT_HG_ROOT_PATH;
  }

  public void setRecentRootPath(@NotNull String recentRootPath) {
    myState.RECENT_HG_ROOT_PATH = recentRootPath;
  }

  public boolean isCheckIncomingOutgoing() {
    return myState.CHECK_INCOMING_OUTGOING;
  }

  public void setCheckIncomingOutgoing(boolean checkIncomingOutgoing) {
    if (myState.CHECK_INCOMING_OUTGOING != checkIncomingOutgoing) {
      myState.CHECK_INCOMING_OUTGOING = checkIncomingOutgoing;
      BackgroundTaskUtil.syncPublisher(myProject, HgVcs.INCOMING_OUTGOING_CHECK_TOPIC).updateVisibility();
    }
  }

  @Override
  public @NotNull Value getSyncSetting() {
    return myState.ROOT_SYNC;
  }

  @Override
  public void setSyncSetting(@NotNull Value syncSetting) {
    myState.ROOT_SYNC = syncSetting;
  }

  @Override
  public boolean shouldSwapSidesInCompareBranches() {
    return myState.SWAP_SIDES_IN_COMPARE_BRANCHES;
  }

  @Override
  public void setSwapSidesInCompareBranches(boolean value) {
    myState.SWAP_SIDES_IN_COMPARE_BRANCHES = value;
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

  public @NotNull DvcsBranchSettings getBranchSettings() {
    return myState.BRANCH_SETTINGS;
  }
}
