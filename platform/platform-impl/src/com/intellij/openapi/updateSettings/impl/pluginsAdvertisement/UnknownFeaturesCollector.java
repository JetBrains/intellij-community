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
package com.intellij.openapi.updateSettings.impl.pluginsAdvertisement;

import com.intellij.openapi.components.*;
import com.intellij.openapi.project.Project;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;
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

  public void registerUnknownRunConfiguration(String configurationId) {
    registerUnknownFeature("com.intellij.configurationType", configurationId, "Run Configuration");
  }
  
  public void registerUnknownFeature(String featureType, String implementationName, String featureDisplayName) {
    final UnknownFeature feature = new UnknownFeature(featureType, featureDisplayName, implementationName);
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
  public void loadState(Element state) {
    myIgnoredUnknownFeatures.clear();
    for (Element element : state.getChildren()) {
      myIgnoredUnknownFeatures.add(
        new UnknownFeature(element.getAttributeValue(FEATURE_ID), null, element.getAttributeValue(IMPLEMENTATION_NAME)));
    }
  }
}
