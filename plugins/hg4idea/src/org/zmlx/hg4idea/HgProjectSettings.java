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

import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;

@State(
  name = "hg4idea.settings",
  storages = @Storage(id = "hg4idea.settings", file = "$PROJECT_FILE$")
)
public class HgProjectSettings implements PersistentStateComponent<HgProjectSettings.State> {

  private boolean myCheckIncoming = true;
  private boolean myCheckOutgoing = true;
  private HgGlobalSettings myGlobalSettings;

  public HgProjectSettings(HgGlobalSettings globalSettings) {
    myGlobalSettings = globalSettings;
  }

  public State getState() {
    final State s = new State();
    s.myCheckIncoming = myCheckIncoming;
    s.myCheckOutgoing = myCheckOutgoing;
    s.myGlobalSettings = myGlobalSettings;
    return s;
  }

  public void loadState(State state) {
    myCheckIncoming = state.myCheckIncoming;
    myCheckOutgoing = state.myCheckOutgoing;
    myGlobalSettings = state.myGlobalSettings;
  }

  public boolean isCheckIncoming() {
    return myCheckIncoming;
  }

  public void setCheckIncoming(boolean checkIncoming) {
    this.myCheckIncoming = checkIncoming;
  }

  public boolean isCheckOutgoing() {
    return myCheckOutgoing;
  }

  public void setCheckOutgoing(boolean checkOutgoing) {
    this.myCheckOutgoing = checkOutgoing;
  }

  public String getHgExecutable() {
    return myGlobalSettings.getHgExecutable();
  }

  public boolean isAutodetectHg() {
    return myGlobalSettings.isAutodetectHg();
  }

  public void enableAutodetectHg() {
    myGlobalSettings.enableAutodetectHg();
  }

  public void setHgExecutable(String text) {
    myGlobalSettings.setHgExecutable(text);
  }

  public static class State {
    private boolean myCheckIncoming;
    private boolean myCheckOutgoing;
    private HgGlobalSettings myGlobalSettings;
  }
}
