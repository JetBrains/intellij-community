/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package org.jetbrains.jps.appengine.model;

/**
* @author nik
*/
public enum PersistenceApi {
  JDO("JDO 2", "JDO", 1), JDO3("JDO 3", "JDO", 2), JPA("JPA 1", "JPA", 1), JPA2("JPA 2", "JPA", 2);
  private final String myDisplayName;
  private final String myEnhancerApiName;
  private final int myEnhancerVersion;

  PersistenceApi(String displayName, String enhancerApiName, int enhancerVersion) {
    myDisplayName = displayName;
    myEnhancerApiName = enhancerApiName;
    myEnhancerVersion = enhancerVersion;
  }

  public String getDisplayName() {
    return myDisplayName;
  }

  public String getEnhancerApiName() {
    return myEnhancerApiName;
  }

  public int getEnhancerVersion() {
    return myEnhancerVersion;
  }
}
