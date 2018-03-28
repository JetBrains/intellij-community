// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.gradle.execution;

import com.intellij.execution.Location;
import com.intellij.execution.actions.ConfigurationContext;
import com.intellij.execution.actions.RunConfigurationProducer;
import com.intellij.openapi.externalSystem.model.execution.ExternalSystemTaskExecutionSettings;
import com.intellij.openapi.externalSystem.service.execution.AbstractExternalSystemTaskConfigurationType;
import com.intellij.openapi.externalSystem.service.execution.ExternalSystemRunConfiguration;
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.gradle.service.execution.GradleExternalTaskConfigurationType;
import org.jetbrains.plugins.gradle.util.GradleConstants;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile;

import java.util.List;

import static org.jetbrains.plugins.gradle.execution.GradleRunnerUtil.getTasksTarget;
import static org.jetbrains.plugins.gradle.execution.GradleRunnerUtil.resolveProjectPath;

/**
 * @author Vladislav.Soroka
 */
public class GradleGroovyScriptRunConfigurationProducer extends RunConfigurationProducer<ExternalSystemRunConfiguration> {
  protected GradleGroovyScriptRunConfigurationProducer() {
    super(GradleExternalTaskConfigurationType.getInstance());
  }

  @Override
  protected boolean setupConfigurationFromContext(ExternalSystemRunConfiguration configuration,
                                                  ConfigurationContext context,
                                                  Ref<PsiElement> sourceElement) {
    ExternalSystemTaskExecutionSettings taskExecutionSettings = configuration.getSettings();
    if (!GradleConstants.SYSTEM_ID.equals(taskExecutionSettings.getExternalSystemId())) return false;

    final Location contextLocation = context.getLocation();
    if (!isFromGroovyGradleScript(contextLocation)) return false;

    final Module module = context.getModule();
    if (module == null) return false;

    if (!ExternalSystemApiUtil.isExternalSystemAwareModule(GradleConstants.SYSTEM_ID, module)) return false;

    final String projectPath = resolveProjectPath(module);
    if (projectPath == null) return false;

    List<String> tasksToRun = getTasksTarget(contextLocation);
    taskExecutionSettings.setExternalProjectPath(projectPath);
    taskExecutionSettings.setTaskNames(tasksToRun);
    configuration.setName(AbstractExternalSystemTaskConfigurationType.generateName(module.getProject(), taskExecutionSettings));
    return true;
  }

  @Override
  public boolean isConfigurationFromContext(ExternalSystemRunConfiguration configuration, ConfigurationContext context) {
    if (configuration == null) return false;
    if (!GradleConstants.SYSTEM_ID.equals(configuration.getSettings().getExternalSystemId())) return false;

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
    if (tasks.isEmpty() && taskNames.isEmpty()) return true;

    if (tasks.containsAll(taskNames) && !taskNames.isEmpty()) return true;
    return false;
  }

  private static boolean isFromGroovyGradleScript(@Nullable Location location) {
    if (location == null) return false;
    PsiElement element = location.getPsiElement();
    PsiFile file = element.getContainingFile();
    if (!(file instanceof GroovyFile)) {
      return false;
    }
    return GradleConstants.EXTENSION.equals(file.getVirtualFile().getExtension());
  }
}
