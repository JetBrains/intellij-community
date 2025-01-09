// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.gradle.model.impl;

import com.dynatrace.hash4j.hashing.HashSink;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.util.containers.FileCollectionFactory;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.builders.*;
import org.jetbrains.jps.builders.storage.BuildDataPaths;
import org.jetbrains.jps.cmdline.ProjectDescriptor;
import org.jetbrains.jps.gradle.model.JpsGradleExtensionService;
import org.jetbrains.jps.incremental.CompileContext;
import org.jetbrains.jps.incremental.relativizer.PathRelativizerService;
import org.jetbrains.jps.indices.IgnoredFileIndex;
import org.jetbrains.jps.indices.ModuleExcludeIndex;
import org.jetbrains.jps.model.JpsModel;
import org.jetbrains.jps.model.java.JpsJavaExtensionService;
import org.jetbrains.jps.model.module.JpsModule;
import org.jetbrains.jps.util.JpsPathUtil;

import java.io.File;
import java.util.*;

/**
 * @author Vladislav.Soroka
 */
public final class GradleResourcesTarget extends ModuleBasedTarget<GradleResourceRootDescriptor> implements BuildTargetHashSupplier {
  GradleResourcesTarget(@NotNull GradleResourcesTargetType type, @NotNull JpsModule module) {
    super(type, module);
  }

  @Override
  public @NotNull String getId() {
    return myModule.getName();
  }

  @Override
  public @NotNull Collection<BuildTarget<?>> computeDependencies(@NotNull BuildTargetRegistry targetRegistry, @NotNull TargetOutputIndex outputIndex) {
    return Collections.emptyList();
  }

  @Override
  public boolean isCompiledBeforeModuleLevelBuilders() {
    return true;
  }

  @Override
  public @NotNull List<GradleResourceRootDescriptor> computeRootDescriptors(@NotNull JpsModel model, @NotNull ModuleExcludeIndex index, @NotNull IgnoredFileIndex ignoredFileIndex, @NotNull BuildDataPaths dataPaths) {
    final List<GradleResourceRootDescriptor> result = new ArrayList<>();

    GradleProjectConfiguration projectConfig = JpsGradleExtensionService.getInstance().getGradleProjectConfiguration(dataPaths);
    GradleModuleResourceConfiguration moduleConfig = projectConfig.moduleConfigurations.get(myModule.getName());
    if (moduleConfig == null) return Collections.emptyList();

    int i = 0;

    for (ResourceRootConfiguration resource : getRootConfigurations(moduleConfig)) {
      result.add(new GradleResourceRootDescriptor(this, resource, i++, moduleConfig.overwrite));
    }
    return result;
  }

  private Collection<ResourceRootConfiguration> getRootConfigurations(@Nullable GradleModuleResourceConfiguration moduleConfig) {
    if (moduleConfig != null) {
      return isTests() ? moduleConfig.testResources : moduleConfig.resources;
    }
    return List.of();
  }

  public GradleModuleResourceConfiguration getModuleResourcesConfiguration(BuildDataPaths dataPaths) {
    final GradleProjectConfiguration projectConfig = JpsGradleExtensionService.getInstance().getGradleProjectConfiguration(dataPaths);
    return projectConfig.moduleConfigurations.get(myModule.getName());
  }

  @Override
  public boolean isTests() {
    return ((GradleResourcesTargetType)getTargetType()).isTests();
  }

  @Override
  public @Nullable GradleResourceRootDescriptor findRootDescriptor(@NotNull String rootId, @NotNull BuildRootIndex rootIndex) {
    for (GradleResourceRootDescriptor descriptor : rootIndex.getTargetRoots(this, null)) {
      if (descriptor.getRootId().equals(rootId)) {
        return descriptor;
      }
    }
    return null;
  }

  @Override
  public @NotNull String getPresentableName() {
    return getTargetType().getTypeId() + ":" + myModule.getName();
  }

  @Override
  public @NotNull Collection<File> getOutputRoots(@NotNull CompileContext context) {
    GradleModuleResourceConfiguration configuration =
      getModuleResourcesConfiguration(context.getProjectDescriptor().dataManager.getDataPaths());
    final Set<File> result = FileCollectionFactory.createCanonicalFileSet();
    final File moduleOutput = getModuleOutputDir();
    for (ResourceRootConfiguration resConfig : getRootConfigurations(configuration)) {
      final File output = getOutputDir(moduleOutput, resConfig, configuration.outputDirectory);
      if (output != null) {
        result.add(output);
      }
    }
    return result;
  }

  public @Nullable File getModuleOutputDir() {
    return JpsJavaExtensionService.getInstance().getOutputDirectory(myModule, isTests());
  }

  public static @Nullable File getOutputDir(@Nullable File moduleOutput, ResourceRootConfiguration config, @Nullable String outputDirectory) {
    if(outputDirectory != null) {
      moduleOutput = JpsPathUtil.urlToFile(outputDirectory);
    }

    if (moduleOutput == null) {
      return null;
    }
    String targetPath = config.targetPath;
    if (targetPath == null || targetPath.isBlank()) {
      return moduleOutput;
    }

    File targetPathFile = new File(targetPath);
    File outputFile = targetPathFile.isAbsolute() ? targetPathFile : new File(moduleOutput, targetPath);
    return new File(FileUtil.toCanonicalPath(outputFile.getPath()));
  }

  @Override
  public void computeConfigurationDigest(@NotNull ProjectDescriptor projectDescriptor, @NotNull HashSink hash) {
    BuildDataPaths dataPaths = projectDescriptor.dataManager.getDataPaths();
    GradleModuleResourceConfiguration configuration = getModuleResourcesConfiguration(dataPaths);
    if (configuration == null) {
      hash.putBoolean(false);
    }
    else {
      hash.putBoolean(true);
      PathRelativizerService pathRelativizerService = projectDescriptor.dataManager.getRelativizer();
      configuration.computeConfigurationHash(isTests(), pathRelativizerService, hash);
    }
  }
}
