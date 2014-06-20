package com.intellij.coverage;

import com.intellij.openapi.components.*;
import com.intellij.openapi.components.StoragePathMacros;
import com.intellij.openapi.project.Project;

/**
 * User: anna
 * Date: 4/28/11
 */
@State(
  name = "CoverageOptionsProvider",
  storages = {
    @Storage( file = StoragePathMacros.WORKSPACE_FILE)
  }
)
public class CoverageOptionsProvider implements PersistentStateComponent<CoverageOptionsProvider.State> {
  private State myState = new State();

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
  public void loadState(State state) {
    myState.myAddOrReplace = state.myAddOrReplace;
    myState.myActivateViewOnRun = state.myActivateViewOnRun;
  }

  public static class State {
    public int myAddOrReplace = 3;
    public boolean myActivateViewOnRun = true;
  }
}
