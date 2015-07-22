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
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.externalSystem.model.ExternalProject;
import com.intellij.openapi.externalSystem.model.ExternalSourceDirectorySet;
import com.intellij.openapi.externalSystem.model.ExternalSourceSet;
import com.intellij.openapi.externalSystem.model.project.ExternalSystemSourceType;
import com.intellij.openapi.externalSystem.service.project.manage.ProjectDataManager;
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil;
import com.intellij.openapi.externalSystem.util.ExternalSystemConstants;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
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
import org.jetbrains.plugins.gradle.service.project.data.ExternalProjectDataService;
import org.jetbrains.plugins.gradle.util.GradleConstants;

import java.io.File;
import java.util.Collection;

public class GradleOrderEnumeratorHandler extends OrderEnumerationHandler {
  private static final Logger LOG = Logger.getInstance(GradleOrderEnumeratorHandler.class);

  public static class FactoryImpl extends Factory {
    @Override
    public boolean isApplicable(@NotNull Project project) {
      return true;
    }

    @Override
    public boolean isApplicable(@NotNull Module module) {
      CompilerModuleExtension compilerModuleExtension = CompilerModuleExtension.getInstance(module);
      if (compilerModuleExtension != null && compilerModuleExtension.isCompilerOutputPathInherited()) return false;
      return ExternalSystemApiUtil.isExternalSystemAwareModule(GradleConstants.SYSTEM_ID, module);
    }

    @Override
    public OrderEnumerationHandler createHandler(@Nullable Module module) {
      return INSTANCE;
    }
  }

  private static final GradleOrderEnumeratorHandler INSTANCE = new GradleOrderEnumeratorHandler();

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

    final ExternalProjectDataService externalProjectDataService =
      (ExternalProjectDataService)ServiceManager.getService(ProjectDataManager.class).getDataService(ExternalProjectDataService.KEY);

    assert externalProjectDataService != null;
    final ExternalProject externalRootProject =
      externalProjectDataService.getRootExternalProject(GradleConstants.SYSTEM_ID, new File(gradleProjectPath));
    if (externalRootProject == null) {
      LOG.debug("Root external project was not yep imported for the project path: " + gradleProjectPath);
      return false;
    }

    ExternalProject externalProject = externalProjectDataService.findExternalProject(externalRootProject, rootModel.getModule());
    if (externalProject == null) return false;

    if (includeTests) {
      addOutputModuleRoots(externalProject.getSourceSets().get("test"), ExternalSystemSourceType.TEST_RESOURCE, result);
    }
    if (includeProduction) {
      addOutputModuleRoots(externalProject.getSourceSets().get("main"), ExternalSystemSourceType.RESOURCE, result);
    }

    return true;
  }

  private static void addOutputModuleRoots(@Nullable ExternalSourceSet externalSourceSet,
                                           @NotNull ExternalSystemSourceType sourceType,
                                           @NotNull Collection<String> result) {
    if (externalSourceSet == null) return;
    final ExternalSourceDirectorySet directorySet = externalSourceSet.getSources().get(sourceType);
    if (directorySet == null) return;

    if (directorySet.isCompilerOutputPathInherited()) return;
    final String path = directorySet.getOutputDir().getAbsolutePath();
    VirtualFile virtualFile = VirtualFileManager.getInstance().findFileByUrl(path);
    if (virtualFile == null) {
      if(!directorySet.getOutputDir().exists()){
        FileUtil.createDirectory(directorySet.getOutputDir());
      }
      ApplicationEx app = (ApplicationEx)ApplicationManager.getApplication();
      if (app.isDispatchThread() || !app.holdsReadLock()) {
        LocalFileSystem.getInstance().refreshAndFindFileByIoFile(directorySet.getOutputDir());
      }
    }
    result.add(VfsUtilCore.pathToUrl(path));
  }
}
