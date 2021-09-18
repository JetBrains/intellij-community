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

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.*;
import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;

@State(name = "HgGlobalSettings", storages = @Storage(value = "hg.xml", roamingType = RoamingType.PER_OS), category = SettingsCategory.TOOLS)
public class HgGlobalSettings implements PersistentStateComponent<HgGlobalSettings.State> {
  private static final int FIVE_MINUTES = 300;

  private State myState = new State();

  public static HgGlobalSettings getInstance() {
    return ApplicationManager.getApplication().getService(HgGlobalSettings.class);
  }

  public static class State {
    public String myHgExecutable = null;
    // visited URL -> login for this URL. Passwords are remembered in the PasswordSafe.
    public Map<String, String> myRememberedUserNames = new HashMap<>();
  }

  @Override
  public State getState() {
    return myState;
  }

  @Override
  public void loadState(@NotNull State state) {
    myState = state;
  }

  /**
   * Returns the remembered username for the specified URL which were accessed while working in the plugin.
   *
   * @param stringUrl the url for which to retrieve the last used username;
   * @return the (probably empty) login remembered for this URL.
   */
  @Nullable
  public String getRememberedUserName(@NotNull String stringUrl) {
    return myState.myRememberedUserNames.get(stringUrl);
  }

  /**
   * Adds the information about visited URL.
   *
   * @param stringUrl String representation of the URL. If null or blank String is passed, nothing is saved.
   * @param username  Login used to access the URL. If null is passed, a blank String is used.
   */
  public void addRememberedUrl(@Nullable String stringUrl, @Nullable String username) {
    if (StringUtil.isEmptyOrSpaces(stringUrl)) {
      return;
    }
    if (username == null) {
      username = "";
    }
    myState.myRememberedUserNames.put(stringUrl, username);
  }

  @Nullable
  public String getHgExecutable() {
    return myState.myHgExecutable;
  }

  public void setHgExecutable(@Nullable String hgExecutable) {
    myState.myHgExecutable = hgExecutable;
  }


  public static int getIncomingCheckIntervalSeconds() {
    return FIVE_MINUTES;
  }
}
