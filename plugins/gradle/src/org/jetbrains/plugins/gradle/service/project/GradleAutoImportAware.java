// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.service.project;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.externalSystem.ExternalSystemAutoImportAware;
import com.intellij.openapi.externalSystem.ExternalSystemManager;
import com.intellij.openapi.externalSystem.importing.ProjectResolverPolicy;
import com.intellij.openapi.externalSystem.settings.AbstractExternalSystemSettings;
import com.intellij.openapi.externalSystem.settings.ExternalProjectSettings;
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.CompilerModuleExtension;
import com.intellij.openapi.roots.CompilerProjectExtension;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.util.SmartList;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.gradle.model.data.BuildScriptClasspathData;
import org.jetbrains.plugins.gradle.properties.GradleDaemonJvmPropertiesFile;
import org.jetbrains.plugins.gradle.properties.GradleLocalPropertiesFile;
import org.jetbrains.plugins.gradle.properties.GradlePropertiesFile;
import org.jetbrains.plugins.gradle.service.execution.GradleUserHomeUtil;
import org.jetbrains.plugins.gradle.settings.DistributionType;
import org.jetbrains.plugins.gradle.settings.GradleProjectSettings;
import org.jetbrains.plugins.gradle.settings.GradleSettings;
import org.jetbrains.plugins.gradle.util.GradleConstants;

import java.io.File;
import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;
import java.util.stream.Stream;

public class GradleAutoImportAware implements ExternalSystemAutoImportAware {
  private static final Logger LOG = Logger.getInstance(GradleAutoImportAware.class);

  @Override
  public @Nullable String getAffectedExternalProjectPath(@NotNull String changedFileOrDirPath, @NotNull Project project) {
    if (!changedFileOrDirPath.endsWith("." + GradleConstants.EXTENSION) &&
        !changedFileOrDirPath.endsWith("." + GradleConstants.KOTLIN_DSL_SCRIPT_EXTENSION)) {
      return null;
    }

    if (isInsideCompileOutput(changedFileOrDirPath, project)) {
      return null;
    }

    File file = new File(changedFileOrDirPath);
    if (file.isDirectory()) {
      return null;
    }

    ExternalSystemManager<?,?,?,?,?> manager = ExternalSystemApiUtil.getManager(GradleConstants.SYSTEM_ID);
    assert manager != null;
    AbstractExternalSystemSettings<?, ?,?> systemSettings = manager.getSettingsProvider().fun(project);
    Collection<? extends ExternalProjectSettings> projectsSettings = systemSettings.getLinkedProjectsSettings();
    if (projectsSettings.isEmpty()) {
      return null;
    }
    Map<String, String> rootPaths = new HashMap<>();
    for (ExternalProjectSettings setting : projectsSettings) {
      if(setting != null) {
        for (String path : setting.getModules()) {
          rootPaths.put(new File(path).getPath(), setting.getExternalProjectPath());
        }
      }
    }

    for (File f = file.getParentFile(); f != null; f = f.getParentFile()) {
      String dirPath = f.getPath();
      if (rootPaths.containsKey(dirPath)) {
        return rootPaths.get(dirPath);
      }
    }
    return null;
  }

  private static boolean isInsideCompileOutput(@NotNull String path, @NotNull Project project) {
    final String url = VfsUtilCore.pathToUrl(path);

    boolean isInsideProjectCompile =
      Optional.ofNullable(CompilerProjectExtension.getInstance(project))
              .map(CompilerProjectExtension::getCompilerOutputUrl)
              .filter(outputUrl -> VfsUtilCore.isEqualOrAncestor(outputUrl, url))
              .isPresent();

    if (isInsideProjectCompile) {
      return true;
    }

    return
      Arrays.stream(ModuleManager.getInstance(project).getModules())
                                                  .map(CompilerModuleExtension::getInstance)
                                                  .filter(Objects::nonNull)
                                                  .flatMap(ex -> Stream.of(ex.getCompilerOutputUrl(), ex.getCompilerOutputUrlForTests()))
                                                  .filter(Objects::nonNull)
                                                  .anyMatch(outputUrl -> VfsUtilCore.isEqualOrAncestor(outputUrl, url));
  }

  @Override
  public @NotNull List<File> getAffectedExternalProjectFiles(@NotNull String externalProjectPath, @NotNull Project project) {
    var settings = GradleSettings.getInstance(project);
    var projectSettings = settings.getLinkedProjectSettings(externalProjectPath);
    if (projectSettings == null) {
      return Collections.emptyList();
    }
    var result = new SmartList<File>();
    GradleAutoReloadSettingsCollector.EP_NAME.forEachExtensionSafe(extension -> {
      var settingsFiles = extension.collectSettingsFiles(project, projectSettings);
      result.addAll(ContainerUtil.map(settingsFiles, it -> it.toFile()));
    });
    return result;
  }

  public static final class GradlePropertiesCollector implements GradleAutoReloadSettingsCollector {

    @Override
    public @NotNull List<Path> collectSettingsFiles(@NotNull Project project, @NotNull GradleProjectSettings projectSettings) {
      Path projectPath = Path.of(projectSettings.getExternalProjectPath());
      List<Path> paths = new SmartList<>();
      paths.addAll(GradlePropertiesFile.getPropertyPaths(project, projectPath));
      paths.add(GradleLocalPropertiesFile.getPropertyPath(projectPath));
      paths.add(GradleDaemonJvmPropertiesFile.getPropertyPath(projectPath));
      paths.add(GradleUserHomeUtil.gradleUserHomeDir().toPath().resolve("init.gradle"));
      return paths;
    }
  }

  public static final class VersionCatalogCollector implements GradleAutoReloadSettingsCollector {

    @Override
    public @NotNull List<Path> collectSettingsFiles(@NotNull Project project, @NotNull GradleProjectSettings projectSettings) {
      var externalProjectPath = projectSettings.getExternalProjectPath();
      var projectNode = ExternalSystemApiUtil.findProjectNode(project, GradleConstants.SYSTEM_ID, externalProjectPath);
      if (projectNode == null) {
        return Collections.emptyList();
      }
      var versionCatalogNode = ExternalSystemApiUtil.find(projectNode, BuildScriptClasspathData.VERSION_CATALOGS);
      if (versionCatalogNode == null) {
        return Collections.emptyList();
      }
      var versionCatalogPaths = versionCatalogNode.getData().getCatalogsLocations();
      return ContainerUtil.map(versionCatalogPaths.values(), it -> Path.of(it));
    }
  }

  public static final class WrapperConfigCollector implements GradleAutoReloadSettingsCollector {
    @Override
    public @NotNull List<Path> collectSettingsFiles(@NotNull Project project, @NotNull GradleProjectSettings projectSettings) {
      if (projectSettings.getDistributionType() == DistributionType.DEFAULT_WRAPPED) {
        Path projectPath = Path.of(projectSettings.getExternalProjectPath());
        return Collections.singletonList(projectPath.resolve("gradle/wrapper/gradle-wrapper.properties"));
      }
      return Collections.emptyList();
    }
  }

  public static final class GradleScriptCollector implements GradleAutoReloadSettingsCollector {

    @Override
    public @NotNull List<Path> collectSettingsFiles(@NotNull Project project, @NotNull GradleProjectSettings projectSettings) {
      List<Path> paths = new SmartList<>();

      for (String modulePath : projectSettings.getModules()) {
        ProgressManager.checkCanceled();

        try {
          Files.walkFileTree(Paths.get(modulePath), EnumSet.noneOf(FileVisitOption.class), 1, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult visitFile(Path path, BasicFileAttributes attrs) {
              String fileName = path.getFileName().toString();
              if (fileName.endsWith('.' + GradleConstants.EXTENSION) ||
                  fileName.endsWith('.' + GradleConstants.KOTLIN_DSL_SCRIPT_EXTENSION)) {
                if (Files.isRegularFile(path)) {
                  paths.add(path);
                }
              }
              return FileVisitResult.CONTINUE;
            }
          });
        }
        catch (IOException | InvalidPathException e) {
          LOG.debug(e);
        }
      }

      return paths;
    }
  }

  @Override
  public boolean isApplicable(@Nullable ProjectResolverPolicy resolverPolicy) {
    return resolverPolicy == null || !resolverPolicy.isPartialDataResolveAllowed();
  }
}
