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
  storages = @Storage(id = "hg4idea.settings", file = "$OPTIONS$/hg4idea.xml")
)
public class HgGlobalSettings implements PersistentStateComponent<HgGlobalSettings> {

  private static final String HG = HgVcs.HG_EXECUTABLE_FILE_NAME;
  private static final int FIVE_MINUTES = 300;

  private String hgExecutable = HG;

  public static String getDefaultExecutable() {
    return HG;
  }

  public String getHgExecutable() {
    return hgExecutable;
  }

  public void setHgExecutable(String hgExecutable) {
    this.hgExecutable = hgExecutable;
  }

  public boolean isAutodetectHg() {
    return HG.equals(hgExecutable);
  }

  public void enableAutodetectHg() {
    hgExecutable = HG;
  }

  public static int getIncomingCheckIntervalSeconds() {
    return FIVE_MINUTES;
  }

  public HgGlobalSettings getState() {
    return this;
  }

  public void loadState(HgGlobalSettings state) {
    hgExecutable = state.hgExecutable;
  }

}
