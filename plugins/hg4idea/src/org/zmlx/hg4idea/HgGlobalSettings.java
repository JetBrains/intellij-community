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

import java.util.LinkedList;
import java.util.List;
import java.util.Map;

@State(
  name = "HgGlobalSettings",
  storages = @Storage(id = "HgGlobalSettings", file = "$APP_CONFIG$/vcs.xml")
)
public class HgGlobalSettings implements PersistentStateComponent<HgGlobalSettings> {

  private static final String HG = HgVcs.HG_EXECUTABLE_FILE_NAME;
  private static final int FIVE_MINUTES = 300;

  private String hgExecutable = HG;

  // visited URL -> list of logins for this URL. Passwords are remembered in the PasswordSafe.
  private Map<String, List<String>> myRememberedUrls = new HashMap<String, List<String>>();

  /**
   * Returns the rememebered urls which were accessed while working in the plugin.
   * @return key is a String representation of a URL, value is the list (probably empty) of logins remembered for this URL.
   */
  @NotNull
  public Map<String, List<String>> getRememberedUrls() {
    return myRememberedUrls;
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
    List<String> list = myRememberedUrls.get(stringUrl);
    if (list == null) {
      list = new LinkedList<String>();
      myRememberedUrls.put(stringUrl, list);
    }
    list.add(username);
  }

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
