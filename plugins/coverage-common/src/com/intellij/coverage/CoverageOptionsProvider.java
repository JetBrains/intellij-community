// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.coverage;

import com.intellij.openapi.components.*;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

@State(
  name = "CoverageOptionsProvider",
  storages = {
    @Storage(StoragePathMacros.WORKSPACE_FILE)
  }
)
public class CoverageOptionsProvider implements PersistentStateComponent<CoverageOptionsProvider.State> {
  private final State myState = new State();

  public static CoverageOptionsProvider getInstance(Project project) {
    return ServiceManager.getService(project, CoverageOptionsProvider.class);
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

  @Override
  public State getState() {
    return myState;
  }

  @Override
  public void loadState(@NotNull State state) {
    myState.myAddOrReplace = state.myAddOrReplace;
    myState.myActivateViewOnRun = state.myActivateViewOnRun;
  }

  public static class State {
    public int myAddOrReplace = 3;
    public boolean myActivateViewOnRun = true;
  }
}
