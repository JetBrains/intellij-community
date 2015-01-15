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

import com.intellij.ProjectTopics;
import com.intellij.compiler.server.BuildManager;
import com.intellij.facet.Facet;
import com.intellij.facet.FacetManager;
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
import com.intellij.openapi.project.ModuleAdapter;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectType;
import com.intellij.openapi.project.ProjectTypeService;
import com.intellij.openapi.roots.CompilerModuleExtension;
import com.intellij.openapi.util.JDOMUtil;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.util.ArrayUtil;
import com.intellij.util.Function;
import com.intellij.util.containers.FactoryMap;
import com.intellij.util.messages.MessageBusConnection;
import com.intellij.util.xmlb.XmlSerializer;
import gnu.trove.THashMap;
import org.jdom.Document;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.gradle.model.impl.*;
import org.jetbrains.plugins.gradle.service.project.data.ExternalProjectDataService;
import org.jetbrains.plugins.gradle.util.GradleConstants;

import java.io.*;
import java.util.List;
import java.util.Map;

/**
 * @author Vladislav.Soroka
 * @since 7/10/2014
 */
public class GradleResourceCompilerConfigurationGenerator {

  private static Logger LOG = Logger.getInstance(GradleResourceCompilerConfigurationGenerator.class);

  @NotNull private final Project myProject;
  private final GradleProjectConfiguration projectConfig;

  private final ExternalProjectDataService myExternalProjectDataService;

  public GradleResourceCompilerConfigurationGenerator(@NotNull final Project project) {
    myProject = project;
    projectConfig = new GradleProjectConfiguration();
    myExternalProjectDataService =
      (ExternalProjectDataService)ServiceManager.getService(ProjectDataManager.class).getDataService(ExternalProjectDataService.KEY);
    assert myExternalProjectDataService != null;

    MessageBusConnection connection = project.getMessageBus().connect(project);
    connection.subscribe(ProjectTopics.MODULES, new ModuleAdapter() {
      public void moduleRemoved(Project project, Module module) {
        projectConfig.moduleConfigurations.remove(module.getName());
      }

      @Override
      public void modulesRenamed(Project project, List<Module> modules, Function<Module, String> oldNameProvider) {
        for (Module module : modules) {
          moduleRemoved(project, module);
        }
      }
    });
  }

  public void generateBuildConfiguration(@NotNull final CompileContext context) {

    if(shouldBeBuiltByExternalSystem(myProject)) return;

    if (!hasGradleModules(context)) return;

    final BuildManager buildManager = BuildManager.getInstance();
    final File projectSystemDir = buildManager.getProjectSystemDirectory(myProject);
    if (projectSystemDir == null) return;

    final File gradleConfigFile = new File(projectSystemDir, GradleProjectConfiguration.CONFIGURATION_FILE_RELATIVE_PATH);

    //noinspection MismatchedQueryAndUpdateOfCollection
    final Map<String, ExternalProject> lazyExternalProjectMap = new FactoryMap<String, ExternalProject>() {
      @Nullable
      @Override
      protected ExternalProject create(String gradleProjectPath) {
        return myExternalProjectDataService.getRootExternalProject(GradleConstants.SYSTEM_ID, new File(gradleProjectPath));
      }
    };

    Map<String, GradleModuleResourceConfiguration> affectedModuleConfigurations = new THashMap<String, GradleModuleResourceConfiguration>();
    for (Module module : context.getCompileScope().getAffectedModules()) {
      if (!ExternalSystemApiUtil.isExternalSystemAwareModule(GradleConstants.SYSTEM_ID, module)) continue;

      if(shouldBeBuiltByExternalSystem(module)) continue;

      final String gradleProjectPath = module.getOptionValue(ExternalSystemConstants.ROOT_PROJECT_PATH_KEY);
      assert gradleProjectPath != null;
      final ExternalProject externalRootProject = lazyExternalProjectMap.get(gradleProjectPath);
      if (externalRootProject == null) {
        context.addMessage(CompilerMessageCategory.ERROR,
                             String.format("Unable to make the module: %s, related gradle configuration was not found. " +
                                           "Please, re-import the Gradle project and try again.",
                                           module.getName()), VfsUtilCore.pathToUrl(gradleProjectPath), -1, -1);
        continue;
      }

      ExternalProject externalProject = myExternalProjectDataService.findExternalProject(externalRootProject, module);
      if (externalProject == null) {
        LOG.warn("Unable to find config for module: " + module.getName());
        continue;
      }

      GradleModuleResourceConfiguration resourceConfig = new GradleModuleResourceConfiguration();
      resourceConfig.id = new ModuleVersion(externalProject.getGroup(), externalProject.getName(), externalProject.getVersion());
      resourceConfig.directory = FileUtil.toSystemIndependentName(externalProject.getProjectDir().getPath());

      final ExternalSourceSet mainSourcesSet = externalProject.getSourceSets().get("main");
      addResources(resourceConfig.resources, mainSourcesSet, ExternalSystemSourceType.RESOURCE);

      final ExternalSourceSet testSourcesSet = externalProject.getSourceSets().get("test");
      addResources(resourceConfig.testResources, testSourcesSet, ExternalSystemSourceType.TEST_RESOURCE);

      final CompilerModuleExtension compilerModuleExtension = CompilerModuleExtension.getInstance(module);
      if(compilerModuleExtension != null && compilerModuleExtension.isCompilerOutputPathInherited()) {
        String outputPath = VfsUtilCore.urlToPath(compilerModuleExtension.getCompilerOutputUrl());
        for (ResourceRootConfiguration resource : resourceConfig.resources) {
          resource.targetPath = outputPath;
        }

        String testOutputPath = VfsUtilCore.urlToPath(compilerModuleExtension.getCompilerOutputUrlForTests());
        for (ResourceRootConfiguration resource : resourceConfig.testResources) {
          resource.targetPath = testOutputPath;
        }
      }

      affectedModuleConfigurations.put(module.getName(), resourceConfig);
    }

    boolean configurationUpdateRequired = context.isRebuild() || !gradleConfigFile.exists();
    if(!configurationUpdateRequired) {
      for (Map.Entry<String, GradleModuleResourceConfiguration> entry : affectedModuleConfigurations.entrySet()) {
        GradleModuleResourceConfiguration currentConfiguration = projectConfig.moduleConfigurations.get(entry.getKey());

        if (currentConfiguration == null || !isSameModuleConfiguration(currentConfiguration, entry.getValue())) {
          configurationUpdateRequired = true;
          break;
        }
      }
    }

    if (configurationUpdateRequired) {
      projectConfig.moduleConfigurations.putAll(affectedModuleConfigurations);

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
  }

  private static boolean isSameModuleConfiguration(GradleModuleResourceConfiguration conf1, GradleModuleResourceConfiguration conf2) {
    return conf1.computeConfigurationHash() == conf2.computeConfigurationHash();
  }

  private static boolean shouldBeBuiltByExternalSystem(@NotNull Project project) {
    // skip resource compilation by IDE for Android projects
    // TODO [vlad] this check should be replaced when an option to make any gradle project with gradle be introduced.
    ProjectType projectType = ProjectTypeService.getProjectType(project);
    if(projectType != null && "Android".equals(projectType.getId())) return true;
    return false;
  }

  private static boolean shouldBeBuiltByExternalSystem(@NotNull Module module) {
    for (Facet facet : FacetManager.getInstance(module).getAllFacets()) {
      if(ArrayUtil.contains(facet.getName(), "Android", "Android-Gradle", "Java-Gradle")) return true;
    }
    return false;
  }

  private static boolean hasGradleModules(@NotNull CompileContext context) {
    for (Module module : context.getCompileScope().getAffectedModules()) {
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
