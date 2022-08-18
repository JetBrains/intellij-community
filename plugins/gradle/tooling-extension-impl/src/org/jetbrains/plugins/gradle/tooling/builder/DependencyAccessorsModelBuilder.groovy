// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.tooling.builder

import org.gradle.api.Project
import org.gradle.api.internal.project.ProjectInternal
import org.gradle.initialization.DependenciesAccessors
import org.gradle.util.GradleVersion
import org.jetbrains.annotations.NotNull
import org.jetbrains.plugins.gradle.model.DefaultDependencyAccessorsModel
import org.jetbrains.plugins.gradle.model.DependencyAccessorsModel
import org.jetbrains.plugins.gradle.tooling.ErrorMessageBuilder
import org.jetbrains.plugins.gradle.tooling.ModelBuilderService

class DependencyAccessorsModelBuilder implements ModelBuilderService {

  @Override
  boolean canBuild(String modelName) {
    return modelName == DependencyAccessorsModel.name
  }

  @Override
  Object buildAll(String modelName, Project project) {
    if (GradleVersion.current().baseVersion >= GradleVersion.version("7.0") && project instanceof ProjectInternal) {
      try {
        DependenciesAccessors accessors = project.services.get(DependenciesAccessors)
        List<String> sources = accessors.sources.asFiles.collect { it.toString() }
        List<String> classes = accessors.classes.asFiles.collect { it.toString() }
        return new DefaultDependencyAccessorsModel(sources, classes)
      } catch (UnknownServiceException | ClassNotFoundException ignored) {
      }
    }
    return null
  }

  @NotNull
  @Override
  ErrorMessageBuilder getErrorMessageBuilder(@NotNull Project project, @NotNull Exception e) {
    return ErrorMessageBuilder
      .create(project, e, "Cannot find dependency accessors")
      .withDescription("Unable to build IntelliJ project settings")
  }
}
