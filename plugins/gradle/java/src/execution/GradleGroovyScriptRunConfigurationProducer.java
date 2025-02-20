// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.execution;

import com.intellij.execution.Location;
import com.intellij.execution.actions.ConfigurationContext;
import com.intellij.openapi.externalSystem.model.execution.ExternalSystemTaskExecutionSettings;
import com.intellij.openapi.externalSystem.service.execution.AbstractExternalSystemTaskConfigurationType;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.VisibleForTesting;
import org.jetbrains.plugins.gradle.service.execution.GradleRunConfiguration;

import java.util.List;

import static org.jetbrains.plugins.gradle.execution.GradleGroovyRunnerUtil.getTasksTarget;
import static org.jetbrains.plugins.gradle.execution.GradleRunnerUtil.isFromGroovyGradleScript;
import static org.jetbrains.plugins.gradle.execution.GradleRunnerUtil.resolveProjectPath;

/**
 * @author Vladislav.Soroka
 */
@ApiStatus.Internal
public final class GradleGroovyScriptRunConfigurationProducer extends GradleRunConfigurationProducer {

  @Override
  @VisibleForTesting
  public boolean setupConfigurationFromContext(@NotNull GradleRunConfiguration configuration,
                                                  @NotNull ConfigurationContext context,
                                                  @NotNull Ref<PsiElement> sourceElement) {
    final Location contextLocation = context.getLocation();
    if (!isFromGroovyGradleScript(contextLocation)) return false;

    final Module module = context.getModule();
    if (module == null) return false;

    String projectPath = resolveProjectPath(module);
    if (projectPath == null) {
      VirtualFile virtualFile = contextLocation.getVirtualFile();
      projectPath = virtualFile != null ? virtualFile.getPath() : null;
    }
    if (projectPath == null) {
      return false;
    }

    List<String> tasksToRun = getTasksTarget(contextLocation);
    ExternalSystemTaskExecutionSettings taskExecutionSettings = configuration.getSettings();
    taskExecutionSettings.setExternalProjectPath(projectPath);
    taskExecutionSettings.setTaskNames(tasksToRun);
    configuration.setName(AbstractExternalSystemTaskConfigurationType.generateName(module.getProject(), taskExecutionSettings));
    return true;
  }

  @Override
  public boolean isConfigurationFromContext(@NotNull GradleRunConfiguration configuration, @NotNull ConfigurationContext context) {
    final Location contextLocation = context.getLocation();
    if (!isFromGroovyGradleScript(contextLocation)) return false;

    if (context.getModule() == null) return false;

    final String projectPath = resolveProjectPath(context.getModule());
    if (projectPath == null) return false;
    if (!StringUtil.equals(projectPath, configuration.getSettings().getExternalProjectPath())) {
      return false;
    }

    List<String> tasks = getTasksTarget(contextLocation);
    List<String> taskNames = configuration.getSettings().getTaskNames();
    if (tasks.isEmpty() && taskNames.isEmpty()) {
      return true;
    }

    if (tasks.containsAll(taskNames) && !taskNames.isEmpty()) return true;
    return false;
  }
}
