// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.gradle.toolingExtension.impl.model.sourceSetDependencyModel;

import com.intellij.gradle.toolingExtension.impl.modelBuilder.Messages;
import com.intellij.gradle.toolingExtension.impl.util.javaPluginUtil.JavaPluginUtil;
import org.gradle.api.Project;
import org.gradle.api.tasks.SourceSetContainer;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.gradle.model.ExternalDependency;
import org.jetbrains.plugins.gradle.model.GradleSourceSetDependencyModel;
import org.jetbrains.plugins.gradle.tooling.AbstractModelBuilderService;
import org.jetbrains.plugins.gradle.tooling.Message;
import org.jetbrains.plugins.gradle.tooling.ModelBuilderContext;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

@ApiStatus.Internal
public class GradleSourceSetDependencyModelBuilder extends AbstractModelBuilderService {

  @Override
  public boolean canBuild(String modelName) {
    return GradleSourceSetDependencyModel.class.getName().equals(modelName);
  }

  @Override
  public Object buildAll(@NotNull String modelName, @NotNull Project project, @NotNull ModelBuilderContext context) {
    DefaultGradleSourceSetDependencyModel sourceSetDependencyModel = new DefaultGradleSourceSetDependencyModel();
    sourceSetDependencyModel.setDependencies(collectDependencies(context, project));
    return sourceSetDependencyModel;
  }

  private static @NotNull Map<String, Collection<ExternalDependency>> collectDependencies(
    @NotNull ModelBuilderContext context,
    @NotNull Project project
  ) {
    if (!Boolean.getBoolean("idea.resolveSourceSetDependencies")) {
      return new LinkedHashMap<>();
    }
    SourceSetContainer sourceSets = JavaPluginUtil.getSourceSetContainer(project);
    if (sourceSets == null) {
      return new LinkedHashMap<>();
    }
    GradleSourceSetDependencyResolver dependencyResolver = new GradleSourceSetDependencyResolver(context, project);
    Map<String, Collection<ExternalDependency>> result = new LinkedHashMap<>();
    sourceSets.forEach(sourceSet -> {
      Collection<ExternalDependency> dependencies = dependencyResolver.resolveDependencies(sourceSet);
      result.put(sourceSet.getName(), dependencies);
    });
    return result;
  }

  @Override
  public void reportErrorMessage(
    @NotNull String modelName,
    @NotNull Project project,
    @NotNull ModelBuilderContext context,
    @NotNull Exception exception
  ) {
    context.getMessageReporter().createMessage()
      .withGroup(Messages.SOURCE_SET_DEPENDENCY_MODEL_GROUP)
      .withKind(Message.Kind.ERROR)
      .withTitle("Source set dependency model building failure")
      .withException(exception)
      .reportMessage(project);
  }
}
