package com.intellij.appengine.facet;

import com.intellij.facet.Facet;
import com.intellij.facet.FacetType;
import com.intellij.javaee.web.facet.WebFacet;
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
    super(AppEngineFacet.ID, "google-app-engine", "Google App Engine", WebFacet.ID);
  }

  public AppEngineFacetConfiguration createDefaultConfiguration() {
    return new AppEngineFacetConfiguration();
  }

  public AppEngineFacet createFacet(@NotNull Module module,
                                    String name,
                                    @NotNull AppEngineFacetConfiguration configuration,
                                    @Nullable Facet underlyingFacet) {
    return new AppEngineFacet(this, module, name, configuration, underlyingFacet);
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
