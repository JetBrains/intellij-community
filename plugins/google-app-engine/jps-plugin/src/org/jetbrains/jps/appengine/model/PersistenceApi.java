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

import org.jetbrains.annotations.Nls;
import org.jetbrains.jps.appengine.build.JavaGoogleAppEngineJpsBundle;

import java.util.function.Supplier;

public enum PersistenceApi {
  JDO(JavaGoogleAppEngineJpsBundle.messagePointer("persistence.api.item.name.jdo"), "JDO", 1),
  JDO3(JavaGoogleAppEngineJpsBundle.messagePointer("persistence.api.item.name.jdo3"), "JDO", 2),
  JPA(JavaGoogleAppEngineJpsBundle.messagePointer("persistence.api.item.name.jpa"), "JPA", 1),
  JPA2(JavaGoogleAppEngineJpsBundle.messagePointer("persistence.api.item.name.jpa2"), "JPA", 2);

  private final Supplier<@Nls String> myDisplayNameSupplier;
  private final String myEnhancerApiName;
  private final int myEnhancerVersion;

  PersistenceApi(Supplier<@Nls String> displayNameSupplier, String enhancerApiName, int enhancerVersion) {
    myDisplayNameSupplier = displayNameSupplier;
    myEnhancerApiName = enhancerApiName;
    myEnhancerVersion = enhancerVersion;
  }

  @Nls
  public String getDisplayName() {
    return myDisplayNameSupplier.get();
  }

  public String getEnhancerApiName() {
    return myEnhancerApiName;
  }

  public int getEnhancerVersion() {
    return myEnhancerVersion;
  }
}
