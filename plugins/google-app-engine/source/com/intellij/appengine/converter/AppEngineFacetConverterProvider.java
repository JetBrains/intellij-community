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
package com.intellij.appengine.converter;

import com.intellij.appengine.facet.AppEngineFacetType;
import com.intellij.conversion.*;
import com.intellij.openapi.util.JDOMUtil;
import com.intellij.util.containers.ContainerUtil;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.model.serialization.facet.JpsFacetSerializer;

import java.util.ArrayList;
import java.util.List;

/**
 * @author nik
 */
public class AppEngineFacetConverterProvider extends ConverterProvider {
  public AppEngineFacetConverterProvider() {
    super("google-app-engine-facet");
  }

  @NotNull
  @Override
  public ProjectConverter createConverter(@NotNull ConversionContext context) {
    return new ProjectConverter() {
      @Nullable
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
    public void process(ModuleSettings settings) throws CannotConvertException {
      List<Element> facetTags = getAppEngineFacetTags(settings);
      for (Element tag : facetTags) {
        tag.detach();
      }
      Element facetTag = ContainerUtil.getFirstItem(facetTags);
      if (facetTag != null) {
        String facetName = facetTag.getAttributeValue(JpsFacetSerializer.NAME_ATTRIBUTE);
        Element configuration = facetTag.getChild(JpsFacetSerializer.CONFIGURATION_TAG);
        settings.addFacetElement(AppEngineFacetType.STRING_ID, facetName, (Element)configuration.clone());
      }
    }

    @NotNull
    private static List<Element> getAppEngineFacetTags(@NotNull ModuleSettings settings) {
      List<Element> appEngineFacetTags = new ArrayList<>();
      for (Element webFacetTag : settings.getFacetElements("web")) {
        for (Element childFacetTag : JDOMUtil.getChildren(webFacetTag, JpsFacetSerializer.FACET_TAG)) {
          if (AppEngineFacetType.STRING_ID.equals(childFacetTag.getAttributeValue(JpsFacetSerializer.TYPE_ATTRIBUTE))) {
            appEngineFacetTags.add(childFacetTag);
          }
        }
      }
      return appEngineFacetTags;
    }
  }
}
