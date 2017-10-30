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
package com.intellij.openapi.updateSettings.impl.pluginsAdvertisement;

import org.jetbrains.annotations.NotNull;

public class UnknownFeature {
  private final String myFeatureType;
  private final String myFeatureDisplayName;
  private final String myImplementationName;
  private final String myImplementationDisplayName;

  public UnknownFeature(@NotNull String featureType,
                        String featureDisplayName,
                        @NotNull String implementationName,
                        String implementationDisplayName) {
    myFeatureType = featureType;
    myFeatureDisplayName = featureDisplayName;
    myImplementationName = implementationName;
    myImplementationDisplayName = implementationDisplayName;
  }

  public String getFeatureType() {
    return myFeatureType;
  }

  public String getImplementationName() {
    return myImplementationName;
  }

  public String getFeatureDisplayName() {
    return myFeatureDisplayName;
  }

  public String getImplementationDisplayName() {
    return myImplementationDisplayName;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    UnknownFeature feature = (UnknownFeature)o;

    if (!myFeatureType.equals(feature.myFeatureType)) return false;
    if (!myImplementationName.equals(feature.myImplementationName)) return false;

    return true;
  }

  @Override
  public int hashCode() {
    int result = myFeatureType.hashCode();
    result = 31 * result + myImplementationName.hashCode();
    return result;
  }
}
