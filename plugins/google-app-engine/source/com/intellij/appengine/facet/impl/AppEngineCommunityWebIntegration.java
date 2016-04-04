/*
 * Copyright 2000-2013 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.appengine.facet.impl;

import com.intellij.appengine.facet.AppEngineWebIntegration;
import com.intellij.appengine.sdk.AppEngineSdk;
import com.intellij.framework.addSupport.FrameworkSupportInModuleProvider;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.packaging.artifacts.Artifact;
import com.intellij.packaging.artifacts.ArtifactManager;
import com.intellij.packaging.artifacts.ArtifactType;
import com.intellij.packaging.elements.PackagingElement;
import com.intellij.packaging.elements.PackagingElementFactory;
import com.intellij.packaging.impl.artifacts.PlainArtifactType;
import com.intellij.util.ArrayUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.Collections;
import java.util.List;

/**
 * @author nik
 */
public class AppEngineCommunityWebIntegration extends AppEngineWebIntegration {
  private static final Logger LOG = Logger.getInstance(AppEngineCommunityWebIntegration.class);

  @Nullable
  @Override
  public VirtualFile suggestParentDirectoryForAppEngineWebXml(@NotNull Module module, @NotNull ModifiableRootModel rootModel) {
    final VirtualFile root = ArrayUtil.getFirstElement(rootModel.getContentRoots());
    if (root != null) {
      try {
        return VfsUtil.createDirectoryIfMissing(root, "WEB-INF");
      }
      catch (IOException e) {
        LOG.info(e);
        return null;
      }
    }
    return null;
  }

  @NotNull
  @Override
  public ArtifactType getAppEngineWebArtifactType() {
    return PlainArtifactType.getInstance();
  }

  @Nullable
  @Override
  public ArtifactType getAppEngineApplicationArtifactType() {
    return null;
  }

  @Override
  public void setupJpaSupport(@NotNull Module module, @NotNull VirtualFile persistenceXml) {
  }

  @Override
  public void setupRunConfiguration(@NotNull AppEngineSdk sdk,
                                    @Nullable Artifact artifact,
                                    @NotNull Project project) {
  }

  @Override
  public void setupDevServer(@NotNull AppEngineSdk sdk) {
  }

  @Override
  public void addDevServerToModuleDependencies(@NotNull ModifiableRootModel rootModel, @NotNull AppEngineSdk sdk) {
  }

  @Override
  public void addLibraryToArtifact(@NotNull Library library, @NotNull Artifact artifact, @NotNull Project project) {
    final ArtifactManager artifactManager = ArtifactManager.getInstance(project);
    for (PackagingElement<?> element : PackagingElementFactory.getInstance().createLibraryElements(library)) {
      final String dir = element.getFilesKind(artifactManager.getResolvingContext()).containsDirectoriesWithClasses() ? "classes" : "lib";
      artifactManager.addElementsToDirectory(artifact, "WEB-INF/" + dir, element);
    }
  }

  @Override
  public List<? extends AppEngineSdk> getSdkForConfiguredDevServers() {
    return Collections.emptyList();
  }

  @Override
  public void addDescriptor(@NotNull Artifact artifact, @NotNull Project project, @NotNull VirtualFile descriptor) {
    ArtifactManager.getInstance(project).addElementsToDirectory(artifact, "WEB-INF", PackagingElementFactory.getInstance().createFileCopy(descriptor.getPath(), null));
  }

  @Override
  @NotNull
  public List<FrameworkSupportInModuleProvider.FrameworkDependency> getAppEngineFrameworkDependencies() {
    return Collections.emptyList();
  }
}
