// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.updateSettings.impl.pluginsAdvertisement;

import com.intellij.openapi.components.*;
import com.intellij.openapi.project.Project;
import com.intellij.util.containers.ContainerUtil;
import org.jdom.Element;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

@State(name = "UnknownFeatures", storages = @Storage(StoragePathMacros.WORKSPACE_FILE))
@Service
public final class UnknownFeaturesCollector implements PersistentStateComponent<Element> {

  private static final @NonNls String FEATURE_ID = "featureType";
  private static final @NonNls String IMPLEMENTATION_NAME = "implementationName";

  private final Set<UnknownFeature> myUnknownFeatures = ContainerUtil.newConcurrentSet();
  private final Set<UnknownFeature> myIgnoredUnknownFeatures = new HashSet<>();

  public static @NotNull UnknownFeaturesCollector getInstance(@NotNull Project project) {
    return project.getService(UnknownFeaturesCollector.class);
  }

  @ApiStatus.Internal
  public boolean registerUnknownFeature(@NotNull UnknownFeature feature) {
    return !isIgnored(feature) && myUnknownFeatures.add(feature);
  }

  @ApiStatus.Experimental
  public boolean unregisterUnknownFeature(@NotNull UnknownFeature feature) {
    return myUnknownFeatures.remove(feature);
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

  @ApiStatus.Experimental
  public @NotNull Set<UnknownFeature> getUnknownFeaturesOfType(@NotNull @NonNls String featureType) {
    return myUnknownFeatures.stream()
      .filter(feature -> feature.getFeatureType().equals(featureType))
      .collect(Collectors.toUnmodifiableSet());
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
