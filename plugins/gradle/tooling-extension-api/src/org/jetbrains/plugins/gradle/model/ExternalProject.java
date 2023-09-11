// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.gradle.model;

import org.gradle.tooling.model.Model;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.Serializable;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * @author Vladislav.Soroka
 */
public interface ExternalProject extends Model, Serializable {

  @NotNull
  String getExternalSystemId();

  @NotNull
  String getId();

  /**
   * @return The Gradle path of the project as evaluated by org.gradle.api.Project.path
   * Note, that for tooling purposes you might almost always want to use getIdentityPath() instead.
   */
  @NotNull
  String getPath();

  /**
   * @return The Gradle path of the project in the current build context.
   * This might not be the same as .getPath() e.g. for composite builds
   * However, this path has to be used to construct proper paths for tasks to execute.
   * Contrary to .getPath() this path is expected to be unique
   */
  @NotNull
  String getIdentityPath();

  @NotNull
  String getName();

  @NotNull
  String getQName();

  @Nullable
  String getDescription();

  @NotNull
  String getGroup();

  @NotNull
  String getVersion();

  @ApiStatus.Experimental
  @Nullable
  String getSourceCompatibility();

  @ApiStatus.Experimental
  @Nullable
  String getTargetCompatibility();

  @NotNull
  Map<String, ? extends ExternalProject> getChildProjects();

  @NotNull
  File getProjectDir();

  @NotNull
  File getBuildDir();

  @Nullable
  File getBuildFile();

  @NotNull
  Map<String, ? extends ExternalTask> getTasks();

  @NotNull
  Map<String, ? extends ExternalSourceSet> getSourceSets();

  /**
   * The paths where the artifacts is constructed
   */
  @NotNull
  List<File> getArtifacts();

  /**
   * The artifacts per configuration.
   *
   * @return a mapping between the name of a configuration and the files associated with it.
   */
  @NotNull
  Map<String, Set<File>> getArtifactsByConfiguration();

  @NotNull GradleSourceSetModel getSourceSetModel();
}
