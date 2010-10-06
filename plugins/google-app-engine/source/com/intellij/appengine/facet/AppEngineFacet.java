package com.intellij.appengine.facet;

import com.intellij.appengine.descriptor.dom.AppEngineWebApp;
import com.intellij.appengine.sdk.AppEngineSdk;
import com.intellij.appengine.sdk.AppEngineSdkManager;
import com.intellij.facet.*;
import com.intellij.javaee.util.JamCommonUtil;
import com.intellij.javaee.web.facet.WebFacet;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiFileSystemItem;
import com.intellij.psi.impl.source.jsp.WebDirectoryUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;

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

  public static Collection<AppEngineFacet> getInstances(Module module) {
    return FacetManager.getInstance(module).getFacetsByType(ID);
  }

  public static FacetType<AppEngineFacet, AppEngineFacetConfiguration> getFacetType() {
    return FacetTypeRegistry.getInstance().findFacetType(ID);
  }

  @NotNull
  public AppEngineSdk getSdk() {
    return AppEngineSdkManager.getInstance().findSdk(getConfiguration().getSdkHomePath());
  }

  @NotNull 
  public WebFacet getWebFacet() {
    return (WebFacet)getUnderlyingFacet();
  }

  @Nullable
  public AppEngineWebApp getDescriptorRoot() {
    final Module module = getModule();
    final PsiFileSystemItem file = WebDirectoryUtil.getWebDirectoryUtil(module.getProject()).findFileByPath("WEB-INF/appengine-web.xml", getWebFacet());
    if (!(file instanceof PsiFile)) return null;
    return JamCommonUtil.getRootElement((PsiFile)file, AppEngineWebApp.class, module);
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
