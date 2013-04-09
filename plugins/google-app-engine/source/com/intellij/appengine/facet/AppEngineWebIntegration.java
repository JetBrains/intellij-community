package com.intellij.appengine.facet;

import com.intellij.appengine.sdk.AppEngineSdk;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.packaging.artifacts.Artifact;
import com.intellij.packaging.artifacts.ArtifactType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

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

  public abstract void setupJpaSupport(@NotNull Module module, @NotNull VirtualFile persistenceXml);

  public abstract void setupRunConfiguration(@NotNull ModifiableRootModel rootModel, @NotNull AppEngineSdk sdk, @Nullable Artifact artifact, @NotNull Project project);

  public abstract void setupDevServer(@NotNull AppEngineSdk sdk);

  public abstract void addLibraryToArtifact(@NotNull Library library, @NotNull Artifact artifact, @NotNull Project project);

  public abstract List<? extends AppEngineSdk> getSdkForConfiguredDevServers();
}
