package com.intellij.appengine.facet;

import com.intellij.appengine.util.AppEngineUtil;
import com.intellij.facet.Facet;
import com.intellij.facet.FacetType;
import com.intellij.facet.autodetecting.FacetDetector;
import com.intellij.facet.autodetecting.FacetDetectorRegistry;
import com.intellij.facet.impl.autodetecting.FacetDetectorRegistryEx;
import com.intellij.j2ee.web.WebUtilImpl;
import com.intellij.javaee.web.facet.WebFacet;
import com.intellij.openapi.module.JavaModuleType;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleType;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.Collection;

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
  public void registerDetectors(FacetDetectorRegistry<AppEngineFacetConfiguration> registry) {
    final FacetDetectorRegistryEx<AppEngineFacetConfiguration> registryEx = (FacetDetectorRegistryEx<AppEngineFacetConfiguration>)registry;
    FacetDetector<VirtualFile, AppEngineFacetConfiguration> detector = new FacetDetector<VirtualFile, AppEngineFacetConfiguration>() {
      @Override
      public AppEngineFacetConfiguration detectFacet(VirtualFile source,
                                                     Collection<AppEngineFacetConfiguration> existentFacetConfigurations) {
        if (!existentFacetConfigurations.isEmpty()) {
          return existentFacetConfigurations.iterator().next();
        }
        return new AppEngineFacetConfiguration();
      }
    };
    registryEx.registerUniversalDetectorByFileNameAndRootTag(AppEngineUtil.APPENGINE_WEB_XML_NAME, "appengine-web-app", detector,
                                                             WebUtilImpl.BY_PARENT_WEB_ROOT_SELECTOR);
  }

  @Override
  public Icon getIcon() {
    return AppEngineUtil.APP_ENGINE_ICON;
  }
}
