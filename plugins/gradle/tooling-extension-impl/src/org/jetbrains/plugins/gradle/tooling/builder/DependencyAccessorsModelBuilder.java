// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.tooling.builder;

import com.intellij.gradle.toolingExtension.impl.modelBuilder.Messages;
import com.intellij.gradle.toolingExtension.util.GradleVersionUtil;
import org.gradle.api.Project;
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.initialization.DependenciesAccessors;
import org.gradle.internal.service.ServiceLookupException;
import org.gradle.internal.service.UnknownServiceException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.gradle.model.DefaultDependencyAccessorsModel;
import org.jetbrains.plugins.gradle.model.DependencyAccessorsModel;
import org.jetbrains.plugins.gradle.tooling.Message;
import org.jetbrains.plugins.gradle.tooling.ModelBuilderContext;
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
    if (GradleVersionUtil.isCurrentGradleAtLeast("7.0") && project instanceof ProjectInternal) {
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

  @Override
  public void reportErrorMessage(
    @NotNull String modelName,
    @NotNull Project project,
    @NotNull ModelBuilderContext context,
    @NotNull Exception exception
  ) {
    context.getMessageReporter().createMessage()
      .withGroup(Messages.DEPENDENCY_ACCESSOR_MODEL_GROUP)
      .withKind(Message.Kind.WARNING)
      .withTitle("Cannot find dependency accessors")
      .withText("Unable to build IntelliJ project settings")
      .withException(exception)
      .reportMessage(project);
  }
}
