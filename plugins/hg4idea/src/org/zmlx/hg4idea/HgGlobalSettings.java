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
import com.intellij.util.containers.HashMap;
import org.apache.commons.lang.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

@State(
  name = "HgGlobalSettings",
  storages = @Storage(id = "HgGlobalSettings", file = "$APP_CONFIG$/vcs.xml")
)
public class HgGlobalSettings implements PersistentStateComponent<HgGlobalSettings.State> {

  private static final String HG = HgVcs.HG_EXECUTABLE_FILE_NAME;
  private static final int FIVE_MINUTES = 300;

  private State myState = new State();

  public static class State {
    public String myHgExecutable = HG;
    public boolean myRunViaBash = false;
    // visited URL -> login for this URL. Passwords are remembered in the PasswordSafe.
    public Map<String, String> myRememberedUserNames = new HashMap<String, String>();
  }

  public State getState() {
    return myState;
  }

  public void loadState(State state) {
    myState = state;
  }

  /**
   * Returns the remembered username for the specified URL which were accessed while working in the plugin.
   * @param stringUrl the url for which to retrieve the last used username;
   * @return the (probably empty) login remembered for this URL.
   */
  @Nullable
  public String getRememberedUserName(@NotNull String stringUrl) {
    return myState.myRememberedUserNames.get(stringUrl);
  }

  /**
   * Adds the information about visited URL.
   * @param stringUrl String representation of the URL. If null or blank String is passed, nothing is saved.
   * @param username  Login used to access the URL. If null is passed, a blank String is used.
   */
  public void addRememberedUrl(@Nullable String stringUrl, @Nullable String username) {
    if (StringUtils.isBlank(stringUrl)) {
      return;
    }
    if (username == null) {
      username = "";
    }
    myState.myRememberedUserNames.put(stringUrl, username);
  }

  public static String getDefaultExecutable() {
    return HG;
  }

  public String getHgExecutable() {
    return myState.myHgExecutable;
  }

  public void setHgExecutable(String hgExecutable) {
    myState.myHgExecutable = hgExecutable;
  }

  public boolean isAutodetectHg() {
    return HG.equals(myState.myHgExecutable);
  }

  public void enableAutodetectHg() {
    myState.myHgExecutable = HG;
  }

  public static int getIncomingCheckIntervalSeconds() {
    return FIVE_MINUTES;
  }

  public boolean isRunViaBash() {
    return myState.myRunViaBash;
  }

  public void setRunViaBash(boolean runViaBash) {
    myState.myRunViaBash = runViaBash;
  }

}
