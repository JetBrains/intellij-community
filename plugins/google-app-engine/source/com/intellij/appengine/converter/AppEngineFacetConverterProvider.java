// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.appengine.converter;

import com.intellij.appengine.facet.AppEngineFacetType;
import com.intellij.conversion.*;
import com.intellij.util.containers.ContainerUtil;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.model.serialization.facet.JpsFacetSerializer;

import java.util.ArrayList;
import java.util.List;

final class AppEngineFacetConverterProvider extends ConverterProvider {
  AppEngineFacetConverterProvider() {
  }

  @NotNull
  @Override
  public ProjectConverter createConverter(@NotNull ConversionContext context) {
    return new ProjectConverter() {
      @NotNull
      @Override
      public ConversionProcessor<ModuleSettings> createModuleFileConverter() {
        return new GoogleAppEngineFacetConversionProcessor();
      }
    };
  }

  @NotNull
  @Override
  public String getConversionDescription() {
    return "Google App Engine facets will be decoupled from Web facets";
  }

  private static class GoogleAppEngineFacetConversionProcessor extends ConversionProcessor<ModuleSettings> {
    @Override
    public boolean isConversionNeeded(ModuleSettings settings) {
      return !getAppEngineFacetTags(settings).isEmpty();
    }

    @Override
    public void process(ModuleSettings settings) {
      List<Element> facetTags = getAppEngineFacetTags(settings);
      for (Element tag : facetTags) {
        tag.detach();
      }
      Element facetTag = ContainerUtil.getFirstItem(facetTags);
      if (facetTag != null) {
        String facetName = facetTag.getAttributeValue(JpsFacetSerializer.NAME_ATTRIBUTE);
        Element configuration = facetTag.getChild(JpsFacetSerializer.CONFIGURATION_TAG);
        settings.addFacetElement(AppEngineFacetType.STRING_ID, facetName, configuration.clone());
      }
    }

    @NotNull
    private static List<Element> getAppEngineFacetTags(@NotNull ModuleSettings settings) {
      List<Element> appEngineFacetTags = new ArrayList<>();
      for (Element webFacetTag : settings.getFacetElements("web")) {
        for (Element childFacetTag : webFacetTag.getChildren(JpsFacetSerializer.FACET_TAG)) {
          if (AppEngineFacetType.STRING_ID.equals(childFacetTag.getAttributeValue(JpsFacetSerializer.TYPE_ATTRIBUTE))) {
            appEngineFacetTags.add(childFacetTag);
          }
        }
      }
      return appEngineFacetTags;
    }
  }
}
