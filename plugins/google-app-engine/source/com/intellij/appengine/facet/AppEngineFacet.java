package com.intellij.appengine.facet;

import com.intellij.appengine.descriptor.dom.AppEngineWebApp;
import com.intellij.appengine.sdk.AppEngineSdk;
import com.intellij.appengine.sdk.AppEngineSdkManager;
import com.intellij.facet.*;
import com.intellij.javaee.util.JamCommonUtil;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.util.containers.ContainerUtil;
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
                        @NotNull AppEngineFacetConfiguration configuration) {
    super(facetType, module, name, configuration, null);
  }

  public static Collection<AppEngineFacet> getInstances(Module module) {
    return FacetManager.getInstance(module).getFacetsByType(ID);
  }

  public static FacetType<AppEngineFacet, AppEngineFacetConfiguration> getFacetType() {
    return FacetTypeRegistry.getInstance().findFacetType(ID);
  }

  @Nullable
  public static AppEngineFacet getAppEngineFacetByModule(@Nullable Module module) {
    if (module == null) return null;
    return ContainerUtil.getFirstItem(getInstances(module));
  }

  @NotNull
  public AppEngineSdk getSdk() {
    return AppEngineSdkManager.getInstance().findSdk(getConfiguration().getSdkHomePath());
  }

  @Nullable
  public static AppEngineWebApp getDescriptorRoot(@Nullable VirtualFile descriptorFile, @NotNull final Project project) {
    if (descriptorFile == null) return null;

    Module module = ModuleUtilCore.findModuleForFile(descriptorFile, project);
    if (module == null) return null;

    PsiFile psiFile = PsiManager.getInstance(project).findFile(descriptorFile);
    if (psiFile == null) return null;

    return JamCommonUtil.getRootElement(psiFile, AppEngineWebApp.class, module);
  }

  public boolean shouldRunEnhancerFor(@NotNull VirtualFile file) {
    for (String path : getConfiguration().getFilesToEnhance()) {
      final VirtualFile toEnhance = LocalFileSystem.getInstance().findFileByPath(path);
      if (toEnhance != null && VfsUtilCore.isAncestor(toEnhance, file, false)) {
        return true;
      }
    }
    return false;
  }
}
