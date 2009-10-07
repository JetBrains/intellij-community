package com.intellij.appengine.facet;

import com.intellij.appengine.util.AppEngineUtil;
import com.intellij.appengine.sdk.AppEngineSdkManager;
import com.intellij.appengine.sdk.AppEngineSdk;
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
import java.util.List;

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
    registryEx.registerUniversalDetectorByFileNameAndRootTag(AppEngineUtil.APP_ENGINE_WEB_XML_NAME, "appengine-web-app", new AppEngineFacetDetector(),
                                                             WebUtilImpl.BY_PARENT_WEB_ROOT_SELECTOR);
  }

  @Override
  public Icon getIcon() {
    return AppEngineUtil.APP_ENGINE_ICON;
  }

  private static class AppEngineFacetDetector extends FacetDetector<VirtualFile, AppEngineFacetConfiguration> {
    @Override
    public AppEngineFacetConfiguration detectFacet(VirtualFile source, Collection<AppEngineFacetConfiguration> existentFacetConfigurations) {
      if (!existentFacetConfigurations.isEmpty()) {
        return existentFacetConfigurations.iterator().next();
      }
      final AppEngineFacetConfiguration configuration = new AppEngineFacetConfiguration();
      final List<? extends AppEngineSdk> sdks = AppEngineSdkManager.getInstance().getValidSdks();
      if (!sdks.isEmpty()) {
        configuration.setSdkHomePath(sdks.get(0).getSdkHomePath());
      }
      return configuration;
    }
  }
}
