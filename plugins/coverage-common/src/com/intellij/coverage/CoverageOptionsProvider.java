/*
 * Copyright 2000-2016 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.coverage;

import com.intellij.openapi.components.*;
import com.intellij.openapi.project.Project;

/**
 * User: anna
 * Date: 4/28/11
 */
@State(
  name = "CoverageOptionsProvider",
  storages = {
    @Storage(StoragePathMacros.WORKSPACE_FILE)
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
