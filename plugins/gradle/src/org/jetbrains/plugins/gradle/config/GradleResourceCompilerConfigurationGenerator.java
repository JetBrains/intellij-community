/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package org.jetbrains.plugins.gradle.config;

import com.intellij.compiler.server.BuildManager;
import com.intellij.openapi.compiler.CompileContext;
import com.intellij.openapi.compiler.CompilerMessageCategory;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.externalSystem.model.ExternalFilter;
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
import com.intellij.openapi.util.JDOMUtil;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.util.containers.FactoryMap;
import com.intellij.util.xmlb.XmlSerializer;
import org.jdom.Document;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.gradle.model.impl.*;
import org.jetbrains.plugins.gradle.service.project.data.ExternalProjectDataService;
import org.jetbrains.plugins.gradle.util.GradleConstants;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Map;

/**
 * @author Vladislav.Soroka
 * @since 7/10/2014
 */
public class GradleResourceCompilerConfigurationGenerator {

  private static Logger LOG = Logger.getInstance(GradleResourceCompilerConfigurationGenerator.class);

  @NotNull private final Project myProject;
  @NotNull private final CompileContext myContext;
  @NotNull private final Map<String, ExternalProject> myExternalProjectMap;
  private final ExternalProjectDataService myExternalProjectDataService;

  public GradleResourceCompilerConfigurationGenerator(@NotNull final Project project, @NotNull final CompileContext context) {
    myProject = project;
    myContext = context;
    myExternalProjectDataService =
      (ExternalProjectDataService)ServiceManager.getService(ProjectDataManager.class).getDataService(ExternalProjectDataService.KEY);
    assert myExternalProjectDataService != null;

    myExternalProjectMap = new FactoryMap<String, ExternalProject>() {
      @Nullable
      @Override
      protected ExternalProject create(String gradleProjectPath) {
        return myExternalProjectDataService.getOrImportRootExternalProject(project, GradleConstants.SYSTEM_ID, new File(gradleProjectPath));
      }
    };
  }

  public void generateBuildConfiguration() {
    if (!hasGradleModules()) return;

    final BuildManager buildManager = BuildManager.getInstance();
    final File projectSystemDir = buildManager.getProjectSystemDirectory(myProject);
    if (projectSystemDir == null) return;

    final File gradleConfigFile = new File(projectSystemDir, GradleProjectConfiguration.CONFIGURATION_FILE_RELATIVE_PATH);

    GradleProjectConfiguration projectConfig = new GradleProjectConfiguration();
    for (Module module : myContext.getCompileScope().getAffectedModules()) {
      if (!ExternalSystemApiUtil.isExternalSystemAwareModule(GradleConstants.SYSTEM_ID, module)) continue;

      final String gradleProjectPath = module.getOptionValue(ExternalSystemConstants.ROOT_PROJECT_PATH_KEY);
      assert gradleProjectPath != null;
      final ExternalProject externalRootProject = myExternalProjectMap.get(gradleProjectPath);
      if (externalRootProject == null) {
        myContext.addMessage(CompilerMessageCategory.ERROR,
                             String.format("Unable to make the module: %s, related gradle module configuration was not imported",
                                           module.getName()),
                             VfsUtilCore.pathToUrl(gradleProjectPath), -1, -1);
        continue;
      }

      ExternalProject externalProject = myExternalProjectDataService.findExternalProject(externalRootProject, module);
      if (externalProject == null) {
        LOG.warn("Unable to find config for module: " + module.getName());
        continue;
      }

      final CompilerModuleExtension compilerModuleExtension = CompilerModuleExtension.getInstance(module);
      assert compilerModuleExtension != null;

      GradleModuleResourceConfiguration resourceConfig = new GradleModuleResourceConfiguration();
      resourceConfig.id = new ModuleVersion(externalProject.getGroup(), externalProject.getName(), externalProject.getVersion());
      resourceConfig.directory = FileUtil.toSystemIndependentName(externalProject.getProjectDir().getPath());

      final ExternalSourceSet mainSourcesSet = externalProject.getSourceSets().get("main");
      addResources(resourceConfig.resources, mainSourcesSet, ExternalSystemSourceType.RESOURCE);

      final ExternalSourceSet testSourcesSet = externalProject.getSourceSets().get("test");
      addResources(resourceConfig.testResources, testSourcesSet, ExternalSystemSourceType.TEST_RESOURCE);

      projectConfig.moduleConfigurations.put(module.getName(), resourceConfig);
    }

    final Document document = new Document(new Element("gradle-project-configuration"));
    XmlSerializer.serializeInto(projectConfig, document.getRootElement());
    buildManager.runCommand(new Runnable() {
      @Override
      public void run() {
        buildManager.clearState(myProject);
        FileUtil.createIfDoesntExist(gradleConfigFile);
        try {
          JDOMUtil.writeDocument(document, gradleConfigFile, "\n");
        }
        catch (IOException e) {
          throw new RuntimeException(e);
        }
      }
    });
  }

  private boolean hasGradleModules() {
    for (Module module : myContext.getCompileScope().getAffectedModules()) {
      if (ExternalSystemApiUtil.isExternalSystemAwareModule(GradleConstants.SYSTEM_ID, module)) return true;
    }
    return false;
  }

  private static void addResources(@NotNull List<ResourceRootConfiguration> container,
                                   @Nullable ExternalSourceSet externalSourceSet,
                                   @NotNull ExternalSystemSourceType sourceType) {
    if (externalSourceSet == null) return;
    final ExternalSourceDirectorySet directorySet = externalSourceSet.getSources().get(sourceType);
    if (directorySet == null) return;

    for (File file : directorySet.getSrcDirs()) {
      final String dir = file.getPath();
      final ResourceRootConfiguration rootConfiguration = new ResourceRootConfiguration();
      rootConfiguration.directory = FileUtil.toSystemIndependentName(dir);
      final String target = directorySet.getOutputDir().getPath();
      rootConfiguration.targetPath = FileUtil.toSystemIndependentName(target);

      rootConfiguration.includes.clear();
      for (String include : directorySet.getIncludes()) {
        rootConfiguration.includes.add(include.trim());
      }
      rootConfiguration.excludes.clear();
      for (String exclude : directorySet.getExcludes()) {
        rootConfiguration.excludes.add(exclude.trim());
      }

      rootConfiguration.isFiltered = !directorySet.getFilters().isEmpty();
      rootConfiguration.filters.clear();
      for (ExternalFilter filter : directorySet.getFilters()) {
        final ResourceRootFilter resourceRootFilter = new ResourceRootFilter();
        resourceRootFilter.filterType = filter.getFilterType();
        resourceRootFilter.properties = filter.getPropertiesAsJsonMap();
        rootConfiguration.filters.add(resourceRootFilter);
      }

      container.add(rootConfiguration);
    }
  }
}
