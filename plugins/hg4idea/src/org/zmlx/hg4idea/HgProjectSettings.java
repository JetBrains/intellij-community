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
public class HgProjectSettings implements PersistentStateComponent<HgProjectSettings> {

  private boolean checkIncoming = true;
  private boolean checkOutgoing = true;

  public boolean isCheckIncoming() {
    return checkIncoming;
  }

  public void setCheckIncoming(boolean checkIncoming) {
    this.checkIncoming = checkIncoming;
  }

  public boolean isCheckOutgoing() {
    return checkOutgoing;
  }

  public void setCheckOutgoing(boolean checkOutgoing) {
    this.checkOutgoing = checkOutgoing;
  }

  public HgProjectSettings getState() {
    return this;
  }

  public void loadState(HgProjectSettings state) {
    checkIncoming = state.checkIncoming;
    checkOutgoing = state.checkOutgoing;
  }

}
