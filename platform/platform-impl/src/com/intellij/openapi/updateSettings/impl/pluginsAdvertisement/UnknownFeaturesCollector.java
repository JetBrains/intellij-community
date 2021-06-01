// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.updateSettings.impl.pluginsAdvertisement;

import com.intellij.openapi.components.*;
import com.intellij.openapi.project.Project;
import org.jdom.Element;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

@State(name = "UnknownFeatures", storages = @Storage(StoragePathMacros.WORKSPACE_FILE))
@Service
public final class UnknownFeaturesCollector implements PersistentStateComponent<Element> {
  private static final @NonNls String FEATURE_ID = "featureType";
  private static final @NonNls String IMPLEMENTATION_NAME = "implementationName";

  private final Set<UnknownFeature> myUnknownFeatures = new HashSet<>();
  private final Set<UnknownFeature> myIgnoredUnknownFeatures = new HashSet<>();

  public static UnknownFeaturesCollector getInstance(Project project) {
    return project.getService(UnknownFeaturesCollector.class);
  }

  public void registerUnknownFeature(@NonNls @NotNull String featureType,
                                     @NonNls @NotNull String implementationName,
                                     @Nls @NotNull String featureDisplayName) {
    registerUnknownFeature(featureType, featureDisplayName, implementationName, null);
  }

  public void registerUnknownFeature(@NonNls @NotNull String featureType,
                                     @Nls @NotNull String featureDisplayName,
                                     @NonNls @NotNull String implementationName,
                                     @Nls @Nullable String implementationDisplayName) {
    UnknownFeature feature = new UnknownFeature(featureType,
                                                featureDisplayName,
                                                implementationName,
                                                implementationDisplayName);
    if (!isIgnored(feature)) {
      myUnknownFeatures.add(feature);
    }
  }

  public boolean isIgnored(@NotNull UnknownFeature feature) {
    return myIgnoredUnknownFeatures.contains(feature);
  }

  public void ignoreFeature(@NotNull UnknownFeature feature) {
    myIgnoredUnknownFeatures.add(feature);
  }

  public @NotNull Set<UnknownFeature> getUnknownFeatures() {
    return Collections.unmodifiableSet(myUnknownFeatures);
  }

  @Override
  public @Nullable Element getState() {
    if (myIgnoredUnknownFeatures.isEmpty()) return null;

    final Element ignored = new Element("ignored");
    for (UnknownFeature feature : myIgnoredUnknownFeatures) {
      final Element option = new Element("option");
      option.setAttribute(FEATURE_ID, feature.getFeatureType());
      option.setAttribute(IMPLEMENTATION_NAME, feature.getImplementationName());
      ignored.addContent(option);
    }
    return ignored;
  }

  @Override
  public void loadState(@NotNull Element state) {
    myIgnoredUnknownFeatures.clear();
    for (Element element : state.getChildren()) {
      String featureType = element.getAttributeValue(FEATURE_ID);
      if (featureType == null) continue;

      String implementationName = element.getAttributeValue(IMPLEMENTATION_NAME);
      if (implementationName == null) continue;

      myIgnoredUnknownFeatures.add(new UnknownFeature(featureType, implementationName));
    }
  }
}
