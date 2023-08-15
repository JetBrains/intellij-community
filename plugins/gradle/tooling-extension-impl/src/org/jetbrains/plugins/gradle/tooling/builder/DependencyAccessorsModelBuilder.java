// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.tooling.builder;

import org.gradle.api.Project;
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.initialization.DependenciesAccessors;
import org.gradle.internal.service.ServiceLookupException;
import org.gradle.internal.service.UnknownServiceException;
import org.gradle.util.GradleVersion;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.gradle.model.DefaultDependencyAccessorsModel;
import org.jetbrains.plugins.gradle.model.DependencyAccessorsModel;
import org.jetbrains.plugins.gradle.tooling.ErrorMessageBuilder;
import org.jetbrains.plugins.gradle.tooling.ModelBuilderService;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

@SuppressWarnings("SSBasedInspection")
public class DependencyAccessorsModelBuilder implements ModelBuilderService {

  @Override
  public boolean canBuild(String modelName) {
    return DependencyAccessorsModel.class.getName().equals(modelName);
  }

  @Override
  public Object buildAll(String modelName, Project project) {
    if (GradleVersion.current().getBaseVersion().compareTo(GradleVersion.version("7.0")) >= 0 && project instanceof ProjectInternal) {
      try {
        DependenciesAccessors accessors = ((ProjectInternal)project).getServices().get(DependenciesAccessors.class);
        List<String> sources = accessors.getSources().getAsFiles().stream().map(Objects::toString).collect(Collectors.toList());
        List<String> classes = accessors.getClasses().getAsFiles().stream().map(Objects::toString).collect(Collectors.toList());
        return new DefaultDependencyAccessorsModel(sources, classes);
      }
      catch (UnknownServiceException | ServiceLookupException ignored) {
      }
    }
    return null;
  }

  @NotNull
  @Override
  public ErrorMessageBuilder getErrorMessageBuilder(@NotNull Project project, @NotNull Exception e) {
    return ErrorMessageBuilder
      .create(project, e, "Cannot find dependency accessors")
      .withDescription("Unable to build IntelliJ project settings");
  }
}
