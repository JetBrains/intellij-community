// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.execution;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.externalSystem.ExternalSystemModulePropertyManager;
import com.intellij.openapi.externalSystem.model.project.ExternalSystemSourceType;
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ModuleRootModel;
import com.intellij.openapi.roots.OrderEnumerationHandler;
import com.intellij.openapi.roots.OrderRootType;
import com.intellij.openapi.vfs.VfsUtilCore;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.gradle.model.ExternalProject;
import org.jetbrains.plugins.gradle.model.ExternalSourceDirectorySet;
import org.jetbrains.plugins.gradle.model.ExternalSourceSet;
import org.jetbrains.plugins.gradle.service.project.data.ExternalProjectDataCache;
import org.jetbrains.plugins.gradle.settings.GradleProjectSettings;
import org.jetbrains.plugins.gradle.settings.GradleSettings;
import org.jetbrains.plugins.gradle.util.GradleConstants;

import java.io.File;
import java.util.Collection;
import java.util.Map;

public class GradleOrderEnumeratorHandler extends OrderEnumerationHandler {
  private static final Logger LOG = Logger.getInstance(GradleOrderEnumeratorHandler.class);
  private final boolean myResolveModulePerSourceSet;

  public GradleOrderEnumeratorHandler(@NotNull Module module) {
    String rootProjectPath = ExternalSystemApiUtil.getExternalRootProjectPath(module);
    if (rootProjectPath != null) {
      GradleProjectSettings settings = GradleSettings.getInstance(module.getProject()).getLinkedProjectSettings(rootProjectPath);
      myResolveModulePerSourceSet = settings != null && settings.isResolveModulePerSourceSet();
    }
    else {
      myResolveModulePerSourceSet = false;
    }
  }

  /**
   * Allows a plugin to replace GradleOrderEnumeration handler with own implementation.
   * <br/><br/>
   * Can be used if a Gradle-specific module should have non-default OrderEnumerationHandler implementation
   * (e.g., Android module)
   */
  public static class FactoryImpl extends Factory {
    private static final ExtensionPointName<FactoryImpl> EP_NAME =
      new ExtensionPointName<>("org.jetbrains.plugins.gradle.orderEnumerationHandlerFactory");

    @Override
    public boolean isApplicable(@NotNull Module module) {
      return ExternalSystemApiUtil.isExternalSystemAwareModule(GradleConstants.SYSTEM_ID, module);
    }

    @Override
    public @NotNull GradleOrderEnumeratorHandler createHandler(@NotNull Module module) {
      for (FactoryImpl factory : EP_NAME.getExtensionList()) {
        if (factory.isApplicable(module)) {
          return factory.createHandler(module);
        }
      }
      return new GradleOrderEnumeratorHandler(module);
    }
  }

  @Override
  public boolean shouldAddRuntimeDependenciesToTestCompilationClasspath() {
    return !myResolveModulePerSourceSet;
  }

  @Override
  public boolean shouldIncludeTestsFromDependentModulesToTestClasspath() {
    return !myResolveModulePerSourceSet;
  }

  @Override
  public boolean shouldProcessDependenciesRecursively() {
    return false;
  }

  @Override
  public boolean areResourceFilesFromSourceRootsCopiedToOutput() {
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

    final String gradleProjectPath = ExternalSystemModulePropertyManager.getInstance(rootModel.getModule()).getRootProjectPath();
    if (gradleProjectPath == null) {
      LOG.warn("Root project path of the Gradle project not found for " + rootModel.getModule());
      return false;
    }

    Project project = rootModel.getModule().getProject();
    final ExternalProjectDataCache externalProjectDataCache = ExternalProjectDataCache.getInstance(project);
    assert externalProjectDataCache != null;
    final ExternalProject externalRootProject = externalProjectDataCache.getRootExternalProject(gradleProjectPath);
    if (externalRootProject == null) {
      LOG.debug("Root external project was not yep imported for the project path: " + gradleProjectPath);
      return false;
    }

    Map<String, ExternalSourceSet> externalSourceSets = externalProjectDataCache.findExternalProject(externalRootProject, rootModel.getModule());
    if (externalSourceSets.isEmpty()) {
      return false;
    }

    boolean isDelegatedBuildEnabled = GradleProjectSettings.isDelegatedBuildEnabled(rootModel.getModule());
    for (ExternalSourceSet sourceSet : externalSourceSets.values()) {
      if (includeTests) {
        if (isDelegatedBuildEnabled) {
          addOutputModuleRoots(sourceSet.getSources().get(ExternalSystemSourceType.TEST), result, true);
        }
        addOutputModuleRoots(sourceSet.getSources().get(ExternalSystemSourceType.TEST_RESOURCE), result, isDelegatedBuildEnabled);
      }
      if (includeProduction) {
        if (isDelegatedBuildEnabled) {
          addOutputModuleRoots(sourceSet.getSources().get(ExternalSystemSourceType.SOURCE), result, true);
        }
        addOutputModuleRoots(sourceSet.getSources().get(ExternalSystemSourceType.RESOURCE), result, isDelegatedBuildEnabled);
      }
    }

    return true;
  }

  private static void addOutputModuleRoots(@Nullable ExternalSourceDirectorySet directorySet,
                                           @NotNull Collection<? super String> result, boolean isGradleAwareMake) {
    if (directorySet == null) return;
    if (isGradleAwareMake) {
      for (File outputDir : directorySet.getGradleOutputDirs()) {
        result.add(VfsUtilCore.pathToUrl(outputDir.getPath()));
      }
    }
    else if (!directorySet.isCompilerOutputPathInherited()) {
      result.add(VfsUtilCore.pathToUrl(directorySet.getOutputDir().getPath()));
    }
  }
}
