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
package org.jetbrains.plugins.gradle.tooling.internal;

import org.gradle.api.Project;
import org.gradle.tooling.provider.model.ToolingModelBuilder;
import org.gradle.util.GradleVersion;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.TestOnly;
import org.jetbrains.plugins.gradle.tooling.ModelBuilderError;
import org.jetbrains.plugins.gradle.tooling.ModelBuilderService;
import org.jetbrains.plugins.gradle.tooling.annotation.TargetVersions;

import java.io.File;
import java.util.ServiceLoader;

/**
 * @author Vladislav.Soroka
 * @since 11/5/13
 */
@SuppressWarnings("UnusedDeclaration")
public class ExtraModelBuilder implements ToolingModelBuilder {
  public static final String GROUP_TAG = "<ij_msg_gr>";
  public static final String NAV_TAG = "<ij_nav>";
  public static final String EOL_TAG = "<eol>";

  private static final String RANGE_TOKEN = " <=> ";
  private static ServiceLoader<ModelBuilderService> buildersLoader =
    ServiceLoader.load(ModelBuilderService.class, ExtraModelBuilder.class.getClassLoader());

  @NotNull
  private final GradleVersion myCurrentGradleVersion;

  public ExtraModelBuilder() {
    this.myCurrentGradleVersion = GradleVersion.current();
  }

  @TestOnly
  public ExtraModelBuilder(@NotNull GradleVersion gradleVersion) {
    this.myCurrentGradleVersion = gradleVersion;
  }

  @Override
  public boolean canBuild(String modelName) {
    for (ModelBuilderService service : buildersLoader) {
      if (service.canBuild(modelName) && isVersionMatch(service)) return true;
    }
    return false;
  }

  @Override
  public Object buildAll(String modelName, Project project) {
    for (ModelBuilderService service : buildersLoader) {
      if (service.canBuild(modelName) && isVersionMatch(service)) {
        try {
          return service.buildAll(modelName, project);
        }
        catch (Exception e) {
          ModelBuilderError builderError = service.getModelBuildError(project, e);
          String group = builderError.getGroup().replaceAll("\r\n|\n\r|\n|\r", " ");
          String msg = builderError.getError().replaceAll("\r\n|\n\r|\n|\r", EOL_TAG);
          final File projectBuildFile = project.getBuildFile();

          project.getLogger().error(
            GROUP_TAG + group + GROUP_TAG +
            (projectBuildFile != null ? (NAV_TAG + projectBuildFile.getPath() + NAV_TAG) : "") +
            msg
          );
        }
        return null;
      }
    }
    throw new IllegalArgumentException("Unsupported model: " + modelName);
  }

  private boolean isVersionMatch(@NotNull ModelBuilderService builderService) {
    TargetVersions targetVersions = builderService.getClass().getAnnotation(TargetVersions.class);
    if (targetVersions == null || targetVersions.value() == null || targetVersions.value().isEmpty()) return true;

    final GradleVersion current = adjust(myCurrentGradleVersion, targetVersions.checkBaseVersions());

    if (targetVersions.value().endsWith("+")) {
      String minVersion = targetVersions.value().substring(0, targetVersions.value().length() - 1);
      return compare(current, minVersion, targetVersions.checkBaseVersions()) >= 0;
    }
    else {
      final int rangeIndex = targetVersions.value().indexOf(RANGE_TOKEN);
      if (rangeIndex != -1) {
        String minVersion = targetVersions.value().substring(0, rangeIndex);
        String maxVersion = targetVersions.value().substring(rangeIndex + RANGE_TOKEN.length());
        return compare(current, minVersion, targetVersions.checkBaseVersions()) >= 0 &&
               compare(current, maxVersion, targetVersions.checkBaseVersions()) <= 0;
      }
      else {
        return compare(current, targetVersions.value(), targetVersions.checkBaseVersions()) == 0;
      }
    }
  }

  private static int compare(@NotNull GradleVersion gradleVersion, @NotNull String otherGradleVersion, boolean checkBaseVersions) {
    return gradleVersion.compareTo(adjust(GradleVersion.version(otherGradleVersion), checkBaseVersions));
  }

  private static GradleVersion adjust(@NotNull GradleVersion version, boolean checkBaseVersions) {
    return checkBaseVersions ? version.getBaseVersion() : version;
  }
}
