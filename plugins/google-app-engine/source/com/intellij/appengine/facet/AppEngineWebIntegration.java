// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.appengine.facet;

import com.intellij.appengine.sdk.AppEngineSdk;
import com.intellij.framework.addSupport.FrameworkSupportInModuleProvider;
import com.intellij.ide.util.frameworkSupport.FrameworkSupportModel;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.packaging.artifacts.Artifact;
import com.intellij.packaging.artifacts.ArtifactType;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public abstract class AppEngineWebIntegration {
  public static AppEngineWebIntegration getInstance() {
    return ApplicationManager.getApplication().getService(AppEngineWebIntegration.class);
  }

  @Nullable
  public abstract VirtualFile suggestParentDirectoryForAppEngineWebXml(@NotNull Module module, @NotNull ModifiableRootModel rootModel);

  @NotNull
  public List<ArtifactType> getAppEngineTargetArtifactTypes() {
    return ContainerUtil.packNullables(getAppEngineWebArtifactType(), getAppEngineApplicationArtifactType());
  }

  @NotNull
  public abstract ArtifactType getAppEngineWebArtifactType();

  @Nullable
  public abstract ArtifactType getAppEngineApplicationArtifactType();

  @NotNull
  public abstract List<FrameworkSupportInModuleProvider.FrameworkDependency> getAppEngineFrameworkDependencies();

  public abstract void setupJpaSupport(@NotNull Module module, @NotNull VirtualFile persistenceXml);

  public abstract void setupRunConfiguration(@NotNull AppEngineSdk sdk, @Nullable Artifact artifact, @NotNull Project project);

  public abstract void setupDevServer(@NotNull AppEngineSdk sdk);

  public abstract void addDevServerToModuleDependencies(@NotNull ModifiableRootModel rootModel, @NotNull AppEngineSdk sdk);

  public abstract void addLibraryToArtifact(@NotNull Library library, @NotNull Artifact artifact, @NotNull Project project);

  public abstract List<? extends AppEngineSdk> getSdkForConfiguredDevServers();

  public void addDescriptor(@NotNull Artifact artifact, @NotNull Project project, @NotNull VirtualFile descriptor) {
  }

  public void registerFrameworkInModel(FrameworkSupportModel model, AppEngineFacet appEngineFacet) {
  }
}
