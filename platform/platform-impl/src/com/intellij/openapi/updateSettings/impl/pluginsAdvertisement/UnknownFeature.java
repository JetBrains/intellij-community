// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.updateSettings.impl.pluginsAdvertisement;

import com.intellij.openapi.util.NlsSafe;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class UnknownFeature {

  private final @NonNls @NotNull String myFeatureType;
  private final @Nls @Nullable String myFeatureDisplayName;
  private final @NonNls @NotNull String myImplementationName;
  private final @Nls @Nullable String myImplementationDisplayName;
  private final @Nls @Nullable String mySuggestionReason;

  public UnknownFeature(@NonNls @NotNull String featureType,
                        @Nls @Nullable String featureDisplayName,
                        @NonNls @NotNull String implementationName,
                        @Nls @Nullable String implementationDisplayName,
                        @Nls @Nullable String suggestionReason) {
    myFeatureType = featureType;
    myFeatureDisplayName = featureDisplayName;
    myImplementationName = implementationName;
    myImplementationDisplayName = implementationDisplayName;
    mySuggestionReason = suggestionReason;
  }

  public UnknownFeature(@NonNls @NotNull String featureType,
                        @Nls @Nullable String featureDisplayName,
                        @NonNls @NotNull String implementationName,
                        @Nls @Nullable String implementationDisplayName) {
    this(featureType, featureDisplayName, implementationName, implementationDisplayName, null);
  }

  public UnknownFeature(@NonNls @NotNull String featureType,
                        @Nls @Nullable String featureDisplayName,
                        @NonNls @NotNull String implementationName) {
    this(featureType, featureDisplayName, implementationName, null);
  }

  public UnknownFeature(@NonNls @NotNull String featureType,
                        @NonNls @NotNull String implementationName) {
    this(featureType, null, implementationName);
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

  public @Nls @NotNull String getImplementationDisplayName() {
    if (myImplementationDisplayName == null) {
      @NlsSafe String implementationNameFallback = myImplementationName;
      return implementationNameFallback;
    }
    return myImplementationDisplayName;
  }

  public @Nls @Nullable String getSuggestionReason() {
    return mySuggestionReason;
  }

  public UnknownFeature withImplementationDisplayName(@NotNull @Nls String implementationDisplayName) {
    return new UnknownFeature(myFeatureType,
                              myFeatureDisplayName,
                              myImplementationName,
                              myImplementationDisplayName == null ? implementationDisplayName : myImplementationDisplayName,
                              mySuggestionReason);
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

  @Override
  public String toString() {
    return "UnknownFeature{" + "myFeatureType='" + myFeatureType + '\'' +
           ", myFeatureDisplayName='" + myFeatureDisplayName + '\'' +
           ", myImplementationName='" + myImplementationName + '\'' +
           ", myImplementationDisplayName='" + myImplementationDisplayName + '\'' +
           ", mySuggestionReason='" + mySuggestionReason + '\'' +
           '}';
  }
}
