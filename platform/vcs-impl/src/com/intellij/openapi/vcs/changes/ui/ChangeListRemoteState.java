/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package com.intellij.openapi.vcs.changes.ui;

public class ChangeListRemoteState {
  // true -> ok
  private final boolean[] myChangeStates;

  public ChangeListRemoteState(final int size) {
    myChangeStates = new boolean[size];
    for (int i = 0; i < myChangeStates.length; i++) {
      myChangeStates[i] = true;
    }
  }

  public void report(final int idx, final boolean state) {
    myChangeStates[idx] = state;
  }

  public boolean getState() {
    boolean result = true;
    for (boolean state : myChangeStates) {
      result &= state;
    }
    return result;
  }
  
  public static class Reporter {
    private final int myIdx;
    private final ChangeListRemoteState myState;

    public Reporter(int idx, ChangeListRemoteState state) {
      myIdx = idx;
      myState = state;
    }

    public void report(final boolean state) {
      myState.report(myIdx, state);
    }
  }
}
