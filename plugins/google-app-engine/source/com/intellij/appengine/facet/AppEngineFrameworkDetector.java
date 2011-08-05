package com.intellij.appengine.facet;

import com.intellij.appengine.sdk.AppEngineSdk;
import com.intellij.appengine.sdk.AppEngineSdkManager;
import com.intellij.appengine.util.AppEngineUtil;
import com.intellij.facet.FacetConfiguration;
import com.intellij.facet.FacetType;
import com.intellij.framework.detection.FacetBasedFrameworkDetector;
import com.intellij.framework.detection.FileContentPattern;
import com.intellij.j2ee.web.WebUtilImpl;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.patterns.ElementPattern;
import com.intellij.util.indexing.FileContent;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Set;

/**
 * @author nik
 */
public class AppEngineFrameworkDetector extends FacetBasedFrameworkDetector<AppEngineFacet,  AppEngineFacetConfiguration> {
  public AppEngineFrameworkDetector() {
    super("appengine-java");
  }

  @Override
  public FacetType<AppEngineFacet, AppEngineFacetConfiguration> getFacetType() {
    return FacetType.findInstance(AppEngineFacetType.class);
  }

  @Override
  public boolean isSuitableUnderlyingFacetConfiguration(FacetConfiguration underlying,
                                                        AppEngineFacetConfiguration configuration,
                                                        Set<VirtualFile> files) {
    return WebUtilImpl.isWebFacetConfigurationContainingFiles(underlying, files);
  }

  @Override
  public void setupFacet(@NotNull AppEngineFacet facet) {
    final List<? extends AppEngineSdk> sdks = AppEngineSdkManager.getInstance().getValidSdks();
    if (!sdks.isEmpty()) {
      facet.getConfiguration().setSdkHomePath(sdks.get(0).getSdkHomePath());
    }
  }

  @NotNull
  @Override
  public FileType getFileType() {
    return AppEngineFrameworkDetector.super.getFileType();
  }

  @NotNull
  @Override
  public ElementPattern<FileContent> createSuitableFilePattern() {
    return FileContentPattern.fileContent().withName(AppEngineUtil.APP_ENGINE_WEB_XML_NAME).xmlWithRootTag("appengine-web-app");
  }
}
