// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.config;

import com.intellij.ProjectTopics;
import com.intellij.compiler.server.BuildManager;
import com.intellij.facet.Facet;
import com.intellij.facet.FacetManager;
import com.intellij.openapi.compiler.CompileContext;
import com.intellij.openapi.compiler.CompilerMessageCategory;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.externalSystem.model.project.ExternalSystemSourceType;
import com.intellij.openapi.externalSystem.service.execution.ExternalSystemExecutionAware;
import com.intellij.openapi.externalSystem.service.execution.TargetEnvironmentConfigurationProvider;
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.ModuleListener;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectType;
import com.intellij.openapi.project.ProjectTypeService;
import com.intellij.openapi.roots.CompilerModuleExtension;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.util.JDOMUtil;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.ArrayUtil;
import com.intellij.util.Function;
import com.intellij.util.PathMapper;
import com.intellij.util.PlatformUtils;
import com.intellij.util.containers.FactoryMap;
import com.intellij.util.xmlb.XmlSerializer;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.gradle.model.impl.*;
import org.jetbrains.plugins.gradle.model.ExternalFilter;
import org.jetbrains.plugins.gradle.model.ExternalProject;
import org.jetbrains.plugins.gradle.model.ExternalSourceDirectorySet;
import org.jetbrains.plugins.gradle.model.ExternalSourceSet;
import org.jetbrains.plugins.gradle.service.project.data.ExternalProjectDataCache;
import org.jetbrains.plugins.gradle.util.GradleBundle;
import org.jetbrains.plugins.gradle.util.GradleConstants;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @author Vladislav.Soroka
 */
public class GradleResourceCompilerConfigurationGenerator {

  private static final Logger LOG = Logger.getInstance(GradleResourceCompilerConfigurationGenerator.class);

  private final @NotNull Project myProject;
  private final @NotNull Map<String, Integer> myModulesConfigurationHash;
  private final ExternalProjectDataCache externalProjectDataCache;

  public GradleResourceCompilerConfigurationGenerator(final @NotNull Project project) {
    myProject = project;
    myModulesConfigurationHash = new ConcurrentHashMap<>();
    externalProjectDataCache = ExternalProjectDataCache.getInstance(project);
    assert externalProjectDataCache != null;

    project.getMessageBus().connect().subscribe(ProjectTopics.MODULES, new ModuleListener() {
      @Override
      public void moduleRemoved(@NotNull Project project, @NotNull Module module) {
        myModulesConfigurationHash.remove(module.getName());
      }

      @Override
      public void modulesRenamed(@NotNull Project project,
                                 @NotNull List<? extends Module> modules,
                                 @NotNull Function<? super Module, String> oldNameProvider) {
        for (Module module : modules) {
          moduleRemoved(project, module);
        }
      }
    });
  }

  public void generateBuildConfiguration(final @NotNull CompileContext context) {

    if (shouldBeBuiltByExternalSystem(myProject)) return;

    if (!hasGradleModules(context)) return;

    final BuildManager buildManager = BuildManager.getInstance();
    final File projectSystemDir = buildManager.getProjectSystemDirectory(myProject);

    final File gradleConfigFile = new File(projectSystemDir, GradleProjectConfiguration.CONFIGURATION_FILE_RELATIVE_PATH);

    final Map<String, GradleModuleResourceConfiguration> affectedGradleModuleConfigurations =
      generateAffectedGradleModulesConfiguration(context);

    if (affectedGradleModuleConfigurations.isEmpty()) return;

    boolean configurationUpdateRequired = context.isRebuild() || !gradleConfigFile.exists();

    final Map<String, Integer> affectedConfigurationHash = new HashMap<>();
    for (Map.Entry<String, GradleModuleResourceConfiguration> entry : affectedGradleModuleConfigurations.entrySet()) {
      Integer moduleLastConfigurationHash = myModulesConfigurationHash.get(entry.getKey());
      int moduleCurrentConfigurationHash = entry.getValue().computeConfigurationHash();
      if (moduleLastConfigurationHash == null || moduleLastConfigurationHash.intValue() != moduleCurrentConfigurationHash) {
        configurationUpdateRequired = true;
      }
      affectedConfigurationHash.put(entry.getKey(), moduleCurrentConfigurationHash);
    }

    final GradleProjectConfiguration projectConfig = loadLastConfiguration(gradleConfigFile);
    projectConfig.moduleConfigurations.putAll(affectedGradleModuleConfigurations);

    final Element element = new Element("gradle-project-configuration");
    XmlSerializer.serializeInto(projectConfig, element);
    final boolean finalConfigurationUpdateRequired = configurationUpdateRequired;
    buildManager.runCommand(() -> {
      if (finalConfigurationUpdateRequired) {
        buildManager.clearState(myProject);
      }
      FileUtil.createIfDoesntExist(gradleConfigFile);
      try {
        JDOMUtil.write(element, gradleConfigFile.toPath());
        myModulesConfigurationHash.putAll(affectedConfigurationHash);
      }
      catch (IOException e) {
        throw new RuntimeException(e);
      }
    });
  }

  private @NotNull GradleProjectConfiguration loadLastConfiguration(@NotNull File gradleConfigFile) {
    final GradleProjectConfiguration projectConfig = new GradleProjectConfiguration();
    if (gradleConfigFile.exists()) {
      try {
        XmlSerializer.deserializeInto(projectConfig, JDOMUtil.load(gradleConfigFile));

        // filter orphan modules
        final Set<String> actualModules = myModulesConfigurationHash.keySet();
        for (Iterator<Map.Entry<String, GradleModuleResourceConfiguration>> iterator =
             projectConfig.moduleConfigurations.entrySet().iterator(); iterator.hasNext(); ) {
          Map.Entry<String, GradleModuleResourceConfiguration> configurationEntry = iterator.next();
          if (!actualModules.contains(configurationEntry.getKey())) {
            iterator.remove();
          }
        }
      }
      catch (Exception e) {
        LOG.info(e);
      }
    }

    return projectConfig;
  }

  private @NotNull Map<String, GradleModuleResourceConfiguration> generateAffectedGradleModulesConfiguration(@NotNull CompileContext context) {
    final Map<String, GradleModuleResourceConfiguration> affectedGradleModuleConfigurations = new HashMap<>();

    final Map<String, ExternalProject> lazyExternalProjectMap = FactoryMap.create(
      gradleProjectPath1 -> externalProjectDataCache.getRootExternalProject(gradleProjectPath1));

    for (Module module : context.getCompileScope().getAffectedModules()) {
      if (!ExternalSystemApiUtil.isExternalSystemAwareModule(GradleConstants.SYSTEM_ID, module)) continue;

      final String gradleProjectPath = ExternalSystemApiUtil.getExternalRootProjectPath(module);
      assert gradleProjectPath != null;

      if (shouldBeBuiltByExternalSystem(module)) continue;

      final ExternalProject externalRootProject = lazyExternalProjectMap.get(gradleProjectPath);
      if (externalRootProject == null) {
        String message = GradleBundle.message("compiler.build.messages.gradle.configuration.not.found", module.getName());
        context.addMessage(CompilerMessageCategory.WARNING, message, null, -1, -1, null, Collections.singleton(module.getName()));
        continue;
      }

      Map<String, ExternalSourceSet> externalSourceSets = externalProjectDataCache.findExternalProject(externalRootProject, module);
      if (externalSourceSets.isEmpty()) {
        LOG.debug("Unable to find source sets config for module: " + module.getName());
        continue;
      }

      VirtualFile[] sourceRoots = ModuleRootManager.getInstance(module).getSourceRoots(true);
      if (sourceRoots.length == 0) continue;

      GradleModuleResourceConfiguration resourceConfig = new GradleModuleResourceConfiguration();
      resourceConfig.id = new ModuleVersion(
        ExternalSystemApiUtil.getExternalProjectGroup(module),
        ExternalSystemApiUtil.getExternalProjectId(module),
        ExternalSystemApiUtil.getExternalProjectVersion(module));

      PathMapper pathMapper = null;
      for (ExternalSystemExecutionAware executionAware : ExternalSystemExecutionAware.getExtensions(GradleConstants.SYSTEM_ID)) {
        TargetEnvironmentConfigurationProvider provider = executionAware
          .getEnvironmentConfigurationProvider(gradleProjectPath, false, context.getProject());
        if (provider != null) {
          pathMapper = provider.getPathMapper();
          break;
        }
      }

      for (ExternalSourceSet sourceSet : externalSourceSets.values()) {
        addResources(resourceConfig.resources, sourceSet.getSources().get(ExternalSystemSourceType.RESOURCE),
                     sourceSet.getSources().get(ExternalSystemSourceType.SOURCE), pathMapper);
        addResources(resourceConfig.testResources, sourceSet.getSources().get(ExternalSystemSourceType.TEST_RESOURCE),
                     sourceSet.getSources().get(ExternalSystemSourceType.TEST), pathMapper);
      }

      boolean useCompilerOutputForResources = PlatformUtils.isFleetBackend();
      final CompilerModuleExtension compilerModuleExtension = CompilerModuleExtension.getInstance(module);
      if (compilerModuleExtension != null &&
          (useCompilerOutputForResources || compilerModuleExtension.isCompilerOutputPathInherited())) {
        String outputPath = VfsUtilCore.urlToPath(compilerModuleExtension.getCompilerOutputUrl());
        for (ResourceRootConfiguration resource : resourceConfig.resources) {
          resource.targetPath = toRemote(pathMapper, outputPath);
        }

        String testOutputPath = VfsUtilCore.urlToPath(compilerModuleExtension.getCompilerOutputUrlForTests());
        for (ResourceRootConfiguration resource : resourceConfig.testResources) {
          resource.targetPath = toRemote(pathMapper, testOutputPath);
        }
      }

      affectedGradleModuleConfigurations.put(module.getName(), resourceConfig);
    }

    return affectedGradleModuleConfigurations;
  }

  private static String toRemote(PathMapper pathMapper, String outputPath) {
    return pathMapper != null ? pathMapper.convertToRemote(outputPath) : outputPath;
  }

  private static boolean shouldBeBuiltByExternalSystem(@NotNull Project project) {
    // skip resource compilation by IDE for Android projects
    // TODO [vlad] this check should be replaced when an option to make any gradle project with gradle be introduced.
    ProjectType projectType = ProjectTypeService.getProjectType(project);
    if (projectType != null && "Android".equals(projectType.getId())) return true;
    return false;
  }

  private static boolean shouldBeBuiltByExternalSystem(@NotNull Module module) {
    for (Facet<?> facet : FacetManager.getInstance(module).getAllFacets()) {
      if (ArrayUtil.contains(facet.getName(), "Android", "Android-Gradle", "Java-Gradle")) return true;
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
                                   final @Nullable ExternalSourceDirectorySet directorySet,
                                   final @Nullable ExternalSourceDirectorySet sourcesDirectorySet,
                                   @Nullable PathMapper pathMapper) {
    if (directorySet == null) return;

    for (File file : directorySet.getSrcDirs()) {
      final String dir = file.getPath();
      final ResourceRootConfiguration rootConfiguration = new ResourceRootConfiguration();
      rootConfiguration.directory = toRemote(pathMapper, FileUtil.toSystemIndependentName(dir));
      final String target = directorySet.getOutputDir().getPath();
      rootConfiguration.targetPath = toRemote(pathMapper, FileUtil.toSystemIndependentName(target));

      rootConfiguration.includes.clear();
      for (String include : directorySet.getPatterns().getIncludes()) {
        rootConfiguration.includes.add(include.trim());
      }
      rootConfiguration.excludes.clear();
      for (String exclude : directorySet.getPatterns().getExcludes()) {
        rootConfiguration.excludes.add(exclude.trim());
      }
      if (sourcesDirectorySet != null && sourcesDirectorySet.getSrcDirs().contains(file)) {
        rootConfiguration.excludes.add("**/*.java");
        rootConfiguration.excludes.add("**/*.scala");
        rootConfiguration.excludes.add("**/*.groovy");
        rootConfiguration.excludes.add("**/*.kt");
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
