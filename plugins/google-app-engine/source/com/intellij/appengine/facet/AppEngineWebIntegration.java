package com.intellij.appengine.facet;

import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.packaging.artifacts.ArtifactType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author nik
 */
public abstract class AppEngineWebIntegration {
  public static AppEngineWebIntegration getInstance() {
    return ServiceManager.getService(AppEngineWebIntegration.class);
  }

  @Nullable
  public abstract VirtualFile suggestParentDirectoryForAppEngineWebXml(@NotNull Module module, @NotNull ModifiableRootModel rootModel);

  @NotNull
  public abstract ArtifactType getAppEngineTargetArtifactType();

  public abstract void setupJpaSupport(Module module, VirtualFile persistenceXml);
}
