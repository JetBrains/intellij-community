// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.coverage;

import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.components.StoragePathMacros;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

@State(name = "CoverageOptionsProvider", storages = @Storage(StoragePathMacros.WORKSPACE_FILE))
@ApiStatus.Internal
public class CoverageOptionsProvider implements PersistentStateComponent<CoverageOptionsProvider.State> {
  private final State myState = new State();

  public static CoverageOptionsProvider getInstance(Project project) {
    return project.getService(CoverageOptionsProvider.class);
  }

  public int getOptionToReplace() {
    return myState.myAddOrReplace;
  }

  public void setOptionsToReplace(int addOrReplace) {
    myState.myAddOrReplace = addOrReplace;
  }

  public boolean activateViewOnRun() {
    return myState.myActivateViewOnRun;
  }

  public void setActivateViewOnRun(boolean state) {
    myState.myActivateViewOnRun = state;
  }

  public boolean showInProjectView() {
    return myState.myShowInProjectView;
  }

  public void setShowInProjectView(boolean state) {
    myState.myShowInProjectView = state;
  }

  @Override
  public State getState() {
    return myState;
  }

  @Override
  public void loadState(@NotNull State state) {
    myState.myAddOrReplace = state.myAddOrReplace;
    myState.myActivateViewOnRun = state.myActivateViewOnRun;
    myState.myShowInProjectView = state.myShowInProjectView;
  }

  public static final int REPLACE_SUITE = 0;
  public static final int ADD_SUITE = 1;
  public static final int IGNORE_SUITE = 2;
  public static final int ASK_ON_NEW_SUITE = 3;

  public static class State {
    public int myAddOrReplace = ASK_ON_NEW_SUITE;
    public boolean myActivateViewOnRun = true;
    public boolean myShowInProjectView = true;
  }
}
