package com.intellij.appengine.facet;

import com.intellij.appengine.sdk.AppEngineSdk;
import com.intellij.appengine.sdk.AppEngineSdkManager;
import com.intellij.facet.Facet;
import com.intellij.facet.FacetType;
import com.intellij.facet.FacetTypeId;
import com.intellij.javaee.web.facet.WebFacet;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;

/**
 * @author nik
 */
public class AppEngineFacet extends Facet<AppEngineFacetConfiguration> {
  public static final FacetTypeId<AppEngineFacet> ID = new FacetTypeId<AppEngineFacet>("appEngine");

  public AppEngineFacet(@NotNull FacetType facetType,
                        @NotNull Module module,
                        @NotNull String name,
                        @NotNull AppEngineFacetConfiguration configuration,
                        @NotNull Facet underlyingFacet) {
    super(facetType, module, name, configuration, underlyingFacet);
  }

  @NotNull
  public AppEngineSdk getSdk() {
    return AppEngineSdkManager.getInstance().findSdk(getConfiguration().getSdkHomePath());
  }

  @NotNull 
  public WebFacet getWebFacet() {
    return (WebFacet)getUnderlyingFacet();
  }

  public boolean shouldRunEnhancerFor(@NotNull VirtualFile file) {
    for (String path : getConfiguration().getFilesToEnhance()) {
      final VirtualFile toEnhance = LocalFileSystem.getInstance().findFileByPath(path);
      if (toEnhance != null && VfsUtil.isAncestor(toEnhance, file, false)) {
        return true;
      }
    }
    return false;
  }
}
