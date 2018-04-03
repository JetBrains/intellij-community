// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.updateSettings.impl.pluginsAdvertisement;

import com.intellij.openapi.components.*;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashSet;
import java.util.Set;

@State(name = "UnknownFeatures", storages = @Storage(StoragePathMacros.WORKSPACE_FILE))
public class UnknownFeaturesCollector implements PersistentStateComponent<Element> {
  @NonNls private static final String FEATURE_ID = "featureType";
  @NonNls private static final String IMPLEMENTATION_NAME = "implementationName";

  private final Set<UnknownFeature> myUnknownFeatures = new HashSet<>();
  private final Set<UnknownFeature> myIgnoredUnknownFeatures = new HashSet<>();

  public static UnknownFeaturesCollector getInstance(Project project) {
    return ServiceManager.getService(project, UnknownFeaturesCollector.class);
  }

  public void registerUnknownRunConfiguration(@NotNull String configurationId, @Nullable String factoryName) {
    registerUnknownFeature("com.intellij.configurationType", configurationId,
                           "Run Configuration", StringUtil.notNullize(factoryName, configurationId));
  }

  public void registerUnknownFeature(@NotNull String featureType, @NotNull String implementationName, @NotNull String featureDisplayName) {
    registerUnknownFeature(featureType, implementationName, featureDisplayName, implementationName);
  }

  public void registerUnknownFeature(@NotNull String featureType, @NotNull String implementationName,
                                     @NotNull String featureDisplayName, @NotNull String implementationDisplayName) {
    final UnknownFeature feature = new UnknownFeature(featureType, featureDisplayName, implementationName, implementationDisplayName);
    if (!isIgnored(feature)) {
      myUnknownFeatures.add(feature);
    }
  }

  public boolean isIgnored(UnknownFeature feature) {
    return myIgnoredUnknownFeatures.contains(feature);
  }

  public void ignoreFeature(UnknownFeature feature) {
    myIgnoredUnknownFeatures.add(feature);
  }

  public Set<UnknownFeature> getUnknownFeatures() {
    return myUnknownFeatures;
  }

  @Nullable
  @Override
  public Element getState() {
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
      myIgnoredUnknownFeatures.add(
        new UnknownFeature(element.getAttributeValue(FEATURE_ID), null, element.getAttributeValue(IMPLEMENTATION_NAME), null));
    }
  }
}
