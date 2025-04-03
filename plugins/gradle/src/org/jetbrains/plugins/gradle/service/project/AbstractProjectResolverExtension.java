// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.service.project;

import com.intellij.openapi.externalSystem.model.DataNode;
import com.intellij.openapi.externalSystem.model.ExternalSystemException;
import com.intellij.openapi.externalSystem.model.project.ModuleData;
import com.intellij.openapi.externalSystem.model.project.ProjectData;
import com.intellij.openapi.externalSystem.model.task.TaskData;
import com.intellij.openapi.externalSystem.util.ExternalSystemConstants;
import com.intellij.openapi.externalSystem.util.Order;
import org.gradle.tooling.model.build.BuildEnvironment;
import org.gradle.tooling.model.idea.IdeaModule;
import org.gradle.tooling.model.idea.IdeaProject;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * {@link AbstractProjectResolverExtension} provides dummy implementation of Gradle project resolver.
 *
 * @author Vladislav.Soroka
 */
@Order(ExternalSystemConstants.UNORDERED)
public abstract class AbstractProjectResolverExtension implements GradleProjectResolverExtension {

  protected @NotNull ProjectResolverContext resolverCtx;
  protected @NotNull GradleProjectResolverExtension nextResolver;

  @Override
  public void setProjectResolverContext(@NotNull ProjectResolverContext projectResolverContext) {
    resolverCtx = projectResolverContext;
  }

  @Override
  public void setNext(@NotNull GradleProjectResolverExtension next) {
    // there always should be at least gradle basic resolver further in the chain
    //noinspection ConstantConditions
    assert next != null;
    nextResolver = next;
  }

  @Override
  public @Nullable GradleProjectResolverExtension getNext() {
    return nextResolver;
  }

  @Override
  public void populateProjectExtraModels(@NotNull IdeaProject gradleProject, @NotNull DataNode<ProjectData> ideProject) {
    nextResolver.populateProjectExtraModels(gradleProject, ideProject);
  }

  @Override
  public @Nullable DataNode<ModuleData> createModule(@NotNull IdeaModule gradleModule, @NotNull DataNode<ProjectData> projectDataNode) {
    return nextResolver.createModule(gradleModule, projectDataNode);
  }

  @Override
  public void populateModuleExtraModels(@NotNull IdeaModule gradleModule, @NotNull DataNode<ModuleData> ideModule) {
    nextResolver.populateModuleExtraModels(gradleModule, ideModule);
  }

  @Override
  public void populateModuleContentRoots(@NotNull IdeaModule gradleModule,
                                         @NotNull DataNode<ModuleData> ideModule) {
    nextResolver.populateModuleContentRoots(gradleModule, ideModule);
  }


  @Override
  public void populateModuleCompileOutputSettings(@NotNull IdeaModule gradleModule,
                                                  @NotNull DataNode<ModuleData> ideModule) {
    nextResolver.populateModuleCompileOutputSettings(gradleModule, ideModule);
  }

  @Override
  public void populateModuleDependencies(@NotNull IdeaModule gradleModule,
                                         @NotNull DataNode<ModuleData> ideModule,
                                         @NotNull DataNode<ProjectData> ideProject) {
    nextResolver.populateModuleDependencies(gradleModule, ideModule, ideProject);
  }

  @Override
  public @NotNull Collection<TaskData> populateModuleTasks(@NotNull IdeaModule gradleModule,
                                                           @NotNull DataNode<ModuleData> ideModule,
                                                           @NotNull DataNode<ProjectData> ideProject)
    throws IllegalArgumentException, IllegalStateException {
    return nextResolver.populateModuleTasks(gradleModule, ideModule, ideProject);
  }

  @Override
  public @NotNull ExternalSystemException getUserFriendlyError(@Nullable BuildEnvironment buildEnvironment,
                                                               @NotNull Throwable error,
                                                               @NotNull String projectPath,
                                                               @Nullable String buildFilePath) {
    return nextResolver.getUserFriendlyError(buildEnvironment, error, projectPath, buildFilePath);
  }
}
