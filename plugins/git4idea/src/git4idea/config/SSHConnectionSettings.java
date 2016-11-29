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
package git4idea.config;

import com.intellij.openapi.components.*;
import com.intellij.util.xmlb.annotations.MapAnnotation;
import com.intellij.util.xmlb.annotations.Property;
import gnu.trove.THashMap;

import java.util.Map;
import java.util.TreeMap;

/**
 * This class stores information related to SSH connections
 */
@State(
  name = "SSHConnectionSettings",
  storages = @Storage(value = "security.xml", roamingType = RoamingType.DISABLED)
)
public class SSHConnectionSettings implements PersistentStateComponent<SSHConnectionSettings.State> {
  /**
   * The last successful hosts, the entries are sorted to save on efforts on sorting during saving and loading
   */
  THashMap<String, String> myLastSuccessful = new THashMap<>();

  /**
   * {@inheritDoc}
   */
  @Override
  public State getState() {
    State s = new State();
    s.setLastSuccessful(new TreeMap<>(myLastSuccessful));
    return s;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public void loadState(State state) {
    myLastSuccessful.clear();
    myLastSuccessful.putAll(state.getLastSuccessful());
  }

  /**
   * Get last successful authentication method
   *
   * @param userName the key in format user@host
   * @return the last successful stored authentication method or null
   */
  public String getLastSuccessful(String userName) {
    return myLastSuccessful.get(userName);
  }

  /**
   * Get last successful authentication method
   *
   * @param userName the key in format user@host
   * @param method   the last successful stored authentication method (null or empty string if entry should be dropped)
   */
  public void setLastSuccessful(String userName, String method) {
    if (null == method || method.length() == 0) {
      myLastSuccessful.remove(userName);
    }
    else {
      myLastSuccessful.put(userName, method);
    }
  }

  /**
   * @return the service instance
   */
  public static SSHConnectionSettings getInstance() {
    return ServiceManager.getService(SSHConnectionSettings.class);
  }

  /**
   * The state for the settings
   */
  public static class State {
    /**
     * The last successful authentications
     */
    private Map<String, String> myLastSuccessful = new TreeMap<>();

    /**
     * @return the last successful authentications
     */
    @Property(surroundWithTag = false)
    @MapAnnotation(surroundKeyWithTag = false, surroundValueWithTag = false, surroundWithTag = false,
                   entryTagName = "successfulAuthentication", keyAttributeName = "user", valueAttributeName = "method")
    public Map<String, String> getLastSuccessful() {
      return myLastSuccessful;
    }

    /**
     * Set last successful authentications
     *
     * @param map from user hosts to methods
     */
    public void setLastSuccessful(Map<String, String> map) {
      myLastSuccessful = map;
    }
  }
}
