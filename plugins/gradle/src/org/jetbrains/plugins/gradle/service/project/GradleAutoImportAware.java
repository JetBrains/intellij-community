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
package org.jetbrains.plugins.gradle.service.project;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.externalSystem.ExternalSystemAutoImportAware;
import com.intellij.openapi.externalSystem.ExternalSystemManager;
import com.intellij.openapi.externalSystem.settings.AbstractExternalSystemSettings;
import com.intellij.openapi.externalSystem.settings.ExternalProjectSettings;
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.util.SmartList;
import com.intellij.util.containers.ContainerUtil;
import org.gradle.initialization.BuildLayoutParameters;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.gradle.settings.DistributionType;
import org.jetbrains.plugins.gradle.settings.GradleProjectSettings;
import org.jetbrains.plugins.gradle.settings.GradleSettings;
import org.jetbrains.plugins.gradle.util.GradleConstants;

import java.io.File;
import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;

/**
 * @author Denis Zhdanov
 * @since 6/8/13 3:49 PM
 */
public class GradleAutoImportAware implements ExternalSystemAutoImportAware {
  private static final Logger LOG = Logger.getInstance(GradleAutoImportAware.class);

  @Nullable
  @Override
  public String getAffectedExternalProjectPath(@NotNull String changedFileOrDirPath, @NotNull Project project) {
    if (!changedFileOrDirPath.endsWith("." + GradleConstants.EXTENSION)) {
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
    Map<String, String> rootPaths = ContainerUtil.newHashMap();
    for (ExternalProjectSettings setting : projectsSettings) {
      if(setting != null) {
        for (String path : setting.getModules()) {
          rootPaths.put(new File(path).getAbsolutePath(), setting.getExternalProjectPath());
        }
      }
    }

    for (File f = file.getParentFile(); f != null; f = f.getParentFile()) {
      String dirPath = f.getAbsolutePath();
      if (rootPaths.containsKey(dirPath)) {
        return rootPaths.get(dirPath);
      }
    }
    return null;
  }

  @Override
  public List<File> getAffectedExternalProjectFiles(String projectPath, @NotNull Project project) {
    final List<File> files = new SmartList<>();

    // add global gradle.properties
    String serviceDirectoryPath = GradleSettings.getInstance(project).getServiceDirectoryPath();
    File gradleUserHomeDir = new BuildLayoutParameters().getGradleUserHomeDir();
    files.add(new File(serviceDirectoryPath != null ? serviceDirectoryPath : gradleUserHomeDir.getPath(), "gradle.properties"));
    // add init script
    files.add(new File(serviceDirectoryPath != null ? serviceDirectoryPath : gradleUserHomeDir.getPath(), "init.gradle"));
    // TODO add init scripts from USER_HOME/.gradle/init.d/ directory

    // add project-specific gradle.properties
    GradleProjectSettings projectSettings = GradleSettings.getInstance(project).getLinkedProjectSettings(projectPath);
    files.add(new File(projectSettings == null ? projectPath : projectSettings.getExternalProjectPath(), "gradle.properties"));

    // add wrapper config file
    if (projectSettings != null && projectSettings.getDistributionType() == DistributionType.DEFAULT_WRAPPED) {
      files.add(new File(projectSettings.getExternalProjectPath(), "gradle/wrapper/gradle-wrapper.properties"));
    }

    // add gradle scripts
    Set<String> subProjectPaths = projectSettings != null && /*!projectSettings.getModules().isEmpty() &&*/
                                  FileUtil.pathsEqual(projectSettings.getExternalProjectPath(), projectPath)
                                  ? projectSettings.getModules() : ContainerUtil.set(projectPath);
    for (String path : subProjectPaths) {
      try {
        Files.walkFileTree(Paths.get(path), EnumSet.noneOf(FileVisitOption.class), 1, new SimpleFileVisitor<Path>() {
          @Override
          public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
            String fileName = file.getFileName().toString();
            if (fileName.endsWith('.' + GradleConstants.EXTENSION) ||
                fileName.endsWith('.' + GradleConstants.KOTLIN_DSL_SCRIPT_EXTENSION)) {
              files.add(file.toFile());
            }
            return FileVisitResult.CONTINUE;
          }
        });
      }
      catch (IOException | InvalidPathException e) {
        LOG.debug(e);
      }
    }

    return files;
  }
}
