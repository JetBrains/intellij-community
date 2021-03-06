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

import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class UnknownFeature {

  private final @NonNls @NotNull String myFeatureType;
  private final @Nls @Nullable String myFeatureDisplayName;
  private final @NonNls @NotNull String myImplementationName;
  private final @Nls @Nullable String myImplementationDisplayName;

  public UnknownFeature(@NonNls @NotNull String featureType,
                        @Nls @Nullable String featureDisplayName,
                        @NonNls @NotNull String implementationName,
                        @Nls @Nullable String implementationDisplayName) {
    myFeatureType = featureType;
    myFeatureDisplayName = featureDisplayName;
    myImplementationName = implementationName;
    myImplementationDisplayName = implementationDisplayName;
  }

  public UnknownFeature(@NonNls @NotNull String featureType,
                        @NonNls @NotNull String implementationName) {
    this(featureType, null, implementationName, null);
  }

  public @NonNls @NotNull String getFeatureType() {
    return myFeatureType;
  }

  public @NonNls @NotNull String getImplementationName() {
    return myImplementationName;
  }

  public @Nls @Nullable String getFeatureDisplayName() {
    return myFeatureDisplayName;
  }

  public @Nls @Nullable String getImplementationDisplayName() {
    return myImplementationDisplayName;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    UnknownFeature feature = (UnknownFeature)o;
    return myFeatureType.equals(feature.myFeatureType) &&
           myImplementationName.equals(feature.myImplementationName);
  }

  @Override
  public int hashCode() {
    int result = myFeatureType.hashCode();
    result = 31 * result + myImplementationName.hashCode();
    return result;
  }
}
