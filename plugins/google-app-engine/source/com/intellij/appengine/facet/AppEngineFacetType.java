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
package com.intellij.appengine.facet;

import com.intellij.facet.Facet;
import com.intellij.facet.FacetType;
import com.intellij.openapi.module.JavaModuleType;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleType;
import icons.GoogleAppEngineIcons;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

/**
 * @author nik
 */
public class AppEngineFacetType extends FacetType<AppEngineFacet,  AppEngineFacetConfiguration> {
  public AppEngineFacetType() {
    super(AppEngineFacet.ID, "google-app-engine", "Google App Engine");
  }

  public AppEngineFacetConfiguration createDefaultConfiguration() {
    return new AppEngineFacetConfiguration();
  }

  public AppEngineFacet createFacet(@NotNull Module module,
                                    String name,
                                    @NotNull AppEngineFacetConfiguration configuration,
                                    @Nullable Facet underlyingFacet) {
    return new AppEngineFacet(this, module, name, configuration);
  }

  public boolean isSuitableModuleType(ModuleType moduleType) {
    return moduleType instanceof JavaModuleType;
  }

  @NotNull
  @Override
  public String getDefaultFacetName() {
    return "Google App Engine";
  }

  @Override
  public Icon getIcon() {
    return GoogleAppEngineIcons.AppEngine;
  }
}
