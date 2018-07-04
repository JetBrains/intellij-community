/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package org.jetbrains.plugins.gradle.execution;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.externalSystem.ExternalSystemModulePropertyManager;
import com.intellij.openapi.externalSystem.model.project.ExternalSystemSourceType;
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.roots.ModuleRootModel;
import com.intellij.openapi.roots.OrderEnumerationHandler;
import com.intellij.openapi.roots.OrderRootType;
import com.intellij.openapi.vfs.VfsUtilCore;
import org.gradle.util.GradleVersion;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.gradle.model.ExternalProject;
import org.jetbrains.plugins.gradle.model.ExternalSourceDirectorySet;
import org.jetbrains.plugins.gradle.model.ExternalSourceSet;
import org.jetbrains.plugins.gradle.service.project.data.ExternalProjectDataCache;
import org.jetbrains.plugins.gradle.settings.GradleLocalSettings;
import org.jetbrains.plugins.gradle.settings.GradleProjectSettings;
import org.jetbrains.plugins.gradle.settings.GradleSettings;
import org.jetbrains.plugins.gradle.settings.GradleSystemRunningSettings;
import org.jetbrains.plugins.gradle.util.GradleConstants;

import java.io.File;
import java.util.Collection;
import java.util.Map;

public class GradleOrderEnumeratorHandler extends OrderEnumerationHandler {
  private static final Logger LOG = Logger.getInstance(GradleOrderEnumeratorHandler.class);
  private final boolean myResolveModulePerSourceSet;
  private final boolean myShouldProcessDependenciesRecursively;

  public GradleOrderEnumeratorHandler(@NotNull Module module) {
    String rootProjectPath = ExternalSystemApiUtil.getExternalRootProjectPath(module);
    if (rootProjectPath != null) {
      GradleProjectSettings settings = GradleSettings.getInstance(module.getProject()).getLinkedProjectSettings(rootProjectPath);
      myResolveModulePerSourceSet = settings != null && settings.isResolveModulePerSourceSet();
      String gradleVersion = GradleLocalSettings.getInstance(module.getProject()).getGradleVersion(rootProjectPath);
      myShouldProcessDependenciesRecursively = gradleVersion != null && GradleVersion.version(gradleVersion).compareTo(GradleVersion.version("2.5")) < 0;
    }
    else {
      myShouldProcessDependenciesRecursively = false;
      myResolveModulePerSourceSet = false;
    }
  }

  public static class FactoryImpl extends Factory {
    private static final ExtensionPointName<FactoryImpl> EP_NAME =
      ExtensionPointName.create("org.jetbrains.plugins.gradle.orderEnumerationHandlerFactory");

    @Override
    public boolean isApplicable(@NotNull Module module) {
      return ExternalSystemApiUtil.isExternalSystemAwareModule(GradleConstants.SYSTEM_ID, module);
    }

    @Override
    public GradleOrderEnumeratorHandler createHandler(@NotNull Module module) {
      for (FactoryImpl factory : EP_NAME.getExtensions()) {
        if (factory.isApplicable(module)) {
          return factory.createHandler(module);
        }
      }
      return new GradleOrderEnumeratorHandler(module);
    }
  }

  @Override
  public boolean shouldAddRuntimeDependenciesToTestCompilationClasspath() {
    return myResolveModulePerSourceSet;
  }

  @Override
  public boolean shouldIncludeTestsFromDependentModulesToTestClasspath() {
    return !myResolveModulePerSourceSet;
  }

  @Override
  public boolean shouldProcessDependenciesRecursively() {
    return myShouldProcessDependenciesRecursively;
  }

  @Override
  public boolean addCustomModuleRoots(@NotNull OrderRootType type,
                                      @NotNull ModuleRootModel rootModel,
                                      @NotNull Collection<String> result,
                                      boolean includeProduction,
                                      boolean includeTests) {
    if (!type.equals(OrderRootType.CLASSES)) return false;
    if (!ExternalSystemApiUtil.isExternalSystemAwareModule(GradleConstants.SYSTEM_ID, rootModel.getModule())) return false;

    final String gradleProjectPath = ExternalSystemModulePropertyManager.getInstance(rootModel.getModule()).getRootProjectPath();
    if (gradleProjectPath == null) {
      LOG.warn("Root project path of the Gradle project not found for " + rootModel.getModule());
      return false;
    }

    final ExternalProjectDataCache externalProjectDataCache = ExternalProjectDataCache.getInstance(rootModel.getModule().getProject());
    assert externalProjectDataCache != null;
    final ExternalProject externalRootProject =
      externalProjectDataCache.getRootExternalProject(GradleConstants.SYSTEM_ID, new File(gradleProjectPath));
    if (externalRootProject == null) {
      LOG.debug("Root external project was not yep imported for the project path: " + gradleProjectPath);
      return false;
    }

    Map<String, ExternalSourceSet> externalSourceSets =
      externalProjectDataCache.findExternalProject(externalRootProject, rootModel.getModule());
    if (externalSourceSets.isEmpty()) return false;

    boolean isGradleAwareMake = GradleSystemRunningSettings.getInstance().isUseGradleAwareMake();
    for (ExternalSourceSet sourceSet : externalSourceSets.values()) {
      if (includeTests) {
        if (isGradleAwareMake) {
          addOutputModuleRoots(sourceSet.getSources().get(ExternalSystemSourceType.TEST), result, true);
        }
        addOutputModuleRoots(sourceSet.getSources().get(ExternalSystemSourceType.TEST_RESOURCE), result, isGradleAwareMake);
      }
      if (includeProduction) {
        if (isGradleAwareMake) {
          addOutputModuleRoots(sourceSet.getSources().get(ExternalSystemSourceType.SOURCE), result, true);
        }
        addOutputModuleRoots(sourceSet.getSources().get(ExternalSystemSourceType.RESOURCE), result, isGradleAwareMake);
      }
    }

    return true;
  }

  private static void addOutputModuleRoots(@Nullable ExternalSourceDirectorySet directorySet,
                                           @NotNull Collection<String> result, boolean isGradleAwareMake) {
    if (directorySet == null) return;
    if (isGradleAwareMake) {
      for (File outputDir : directorySet.getGradleOutputDirs()) {
        result.add(VfsUtilCore.pathToUrl(outputDir.getAbsolutePath()));
      }
    }
    else if (!directorySet.isCompilerOutputPathInherited()) {
      result.add(VfsUtilCore.pathToUrl(directorySet.getOutputDir().getAbsolutePath()));
    }
  }
}
