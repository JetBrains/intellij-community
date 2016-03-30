/*
 * Copyright 2000-2015 JetBrains s.r.o.
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

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ex.ApplicationEx;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.externalSystem.model.project.ExternalSystemSourceType;
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil;
import com.intellij.openapi.externalSystem.util.ExternalSystemConstants;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.roots.CompilerModuleExtension;
import com.intellij.openapi.roots.ModuleRootModel;
import com.intellij.openapi.roots.OrderEnumerationHandler;
import com.intellij.openapi.roots.OrderRootType;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.gradle.model.ExternalProject;
import org.jetbrains.plugins.gradle.model.ExternalSourceDirectorySet;
import org.jetbrains.plugins.gradle.model.ExternalSourceSet;
import org.jetbrains.plugins.gradle.service.project.data.ExternalProjectDataCache;
import org.jetbrains.plugins.gradle.util.GradleConstants;

import java.io.File;
import java.util.Collection;
import java.util.Map;

public class GradleOrderEnumeratorHandler extends OrderEnumerationHandler {
  private static final Logger LOG = Logger.getInstance(GradleOrderEnumeratorHandler.class);

  public static class FactoryImpl extends Factory {
    @Override
    public boolean isApplicable(@NotNull Module module) {
      CompilerModuleExtension compilerModuleExtension = CompilerModuleExtension.getInstance(module);
      if (compilerModuleExtension != null && compilerModuleExtension.isCompilerOutputPathInherited()) return false;
      return ExternalSystemApiUtil.isExternalSystemAwareModule(GradleConstants.SYSTEM_ID, module);
    }

    @Override
    public OrderEnumerationHandler createHandler(@NotNull Module module) {
      return INSTANCE;
    }
  }

  private static final GradleOrderEnumeratorHandler INSTANCE = new GradleOrderEnumeratorHandler();

  @Override
  public boolean shouldAddRuntimeDependenciesToTestCompilationClasspath() {
    return true;
  }

  @Override
  public boolean shouldIncludeTestsFromDependentModulesToTestClasspath() {
    return false;
  }

  @Override
  public boolean shouldProcessDependenciesRecursively() {
    return false;
  }

  @Override
  public boolean addCustomModuleRoots(@NotNull OrderRootType type,
                                      @NotNull ModuleRootModel rootModel,
                                      @NotNull Collection<String> result,
                                      boolean includeProduction,
                                      boolean includeTests) {
    if (!type.equals(OrderRootType.CLASSES)) return false;
    if (!ExternalSystemApiUtil.isExternalSystemAwareModule(GradleConstants.SYSTEM_ID, rootModel.getModule())) return false;

    final String gradleProjectPath = rootModel.getModule().getOptionValue(ExternalSystemConstants.ROOT_PROJECT_PATH_KEY);
    if (gradleProjectPath == null) {
      LOG.error("Root project path of the Gradle project not found for " + rootModel.getModule());
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

    for (ExternalSourceSet sourceSet : externalSourceSets.values()) {
      if (includeTests) {
        addOutputModuleRoots(sourceSet.getSources().get(ExternalSystemSourceType.TEST_RESOURCE), result);
      }
      if (includeProduction) {
        addOutputModuleRoots(sourceSet.getSources().get(ExternalSystemSourceType.RESOURCE), result);
      }
    }

    return true;
  }

  private static void addOutputModuleRoots(@Nullable ExternalSourceDirectorySet directorySet,
                                           @NotNull Collection<String> result) {
    if (directorySet == null) return;

    if (directorySet.isCompilerOutputPathInherited()) return;
    final String path = directorySet.getOutputDir().getAbsolutePath();
    result.add(VfsUtilCore.pathToUrl(path));
  }
}
