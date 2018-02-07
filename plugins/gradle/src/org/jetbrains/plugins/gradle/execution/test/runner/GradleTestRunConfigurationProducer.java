/*
 * Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */
package org.jetbrains.plugins.gradle.execution.test.runner;

import com.intellij.execution.RunManagerEx;
import com.intellij.execution.RunnerAndConfigurationSettings;
import com.intellij.execution.actions.ConfigurationContext;
import com.intellij.execution.actions.ConfigurationFromContext;
import com.intellij.execution.actions.RunConfigurationProducer;
import com.intellij.execution.configurations.ConfigurationType;
import com.intellij.openapi.externalSystem.model.DataNode;
import com.intellij.openapi.externalSystem.model.ExternalProjectInfo;
import com.intellij.openapi.externalSystem.model.ProjectKeys;
import com.intellij.openapi.externalSystem.model.project.ModuleData;
import com.intellij.openapi.externalSystem.model.task.TaskData;
import com.intellij.openapi.externalSystem.service.execution.ExternalSystemRunConfiguration;
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil;
import com.intellij.openapi.externalSystem.util.ExternalSystemUtil;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiElement;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.gradle.service.project.GradleProjectResolverUtil;
import org.jetbrains.plugins.gradle.service.resolve.GradleCommonClassNames;
import org.jetbrains.plugins.gradle.settings.GradleSystemRunningSettings;
import org.jetbrains.plugins.gradle.util.GradleConstants;

import java.util.List;

import static org.jetbrains.plugins.gradle.settings.GradleSystemRunningSettings.PreferredTestRunner.*;

/**
 * @author Vladislav.Soroka
 * @since 2/26/2015
 */
public abstract class GradleTestRunConfigurationProducer extends RunConfigurationProducer<ExternalSystemRunConfiguration> {

  private static final List<String> TEST_SOURCE_SET_TASKS = ContainerUtil.list("cleanTest", "test");

  protected GradleTestRunConfigurationProducer(ConfigurationType configurationType) {
    super(configurationType);
  }

  @Override
  public boolean isPreferredConfiguration(ConfigurationFromContext self, ConfigurationFromContext other) {
    return GradleSystemRunningSettings.getInstance().getPreferredTestRunner() == CHOOSE_PER_TEST ||
           GradleSystemRunningSettings.getInstance().getPreferredTestRunner() == GRADLE_TEST_RUNNER;
  }

  @Override
  public boolean shouldReplace(@NotNull ConfigurationFromContext self, @NotNull ConfigurationFromContext other) {
    return GradleSystemRunningSettings.getInstance().getPreferredTestRunner() == GRADLE_TEST_RUNNER;
  }

  @Override
  protected boolean setupConfigurationFromContext(ExternalSystemRunConfiguration configuration,
                                                  ConfigurationContext context,
                                                  Ref<PsiElement> sourceElement) {
    if (!GradleConstants.SYSTEM_ID.equals(configuration.getSettings().getExternalSystemId())) return false;
    if (GradleSystemRunningSettings.getInstance().getPreferredTestRunner() == PLATFORM_TEST_RUNNER) return false;

    return doSetupConfigurationFromContext(configuration, context, sourceElement);
  }

  protected abstract boolean doSetupConfigurationFromContext(ExternalSystemRunConfiguration configuration,
                                                             ConfigurationContext context,
                                                             Ref<PsiElement> sourceElement);

  @Override
  public boolean isConfigurationFromContext(ExternalSystemRunConfiguration configuration, ConfigurationContext context) {
    if (GradleSystemRunningSettings.getInstance().getPreferredTestRunner() == PLATFORM_TEST_RUNNER) return false;
    if (configuration == null) return false;
    if (!GradleConstants.SYSTEM_ID.equals(configuration.getSettings().getExternalSystemId())) return false;

    return doIsConfigurationFromContext(configuration, context);
  }

  protected abstract boolean doIsConfigurationFromContext(ExternalSystemRunConfiguration configuration, ConfigurationContext context);

  @Nullable
  protected String resolveProjectPath(@NotNull Module module) {
    final String rootProjectPath = ExternalSystemApiUtil.getExternalRootProjectPath(module);
    String projectPath = ExternalSystemApiUtil.getExternalProjectPath(module);

    if (rootProjectPath == null || projectPath == null) return null;
    if (!FileUtil.isAncestor(rootProjectPath, projectPath, false)) {
      projectPath = rootProjectPath;
    }
    return projectPath;
  }

  @NotNull
  public static List<String> getTasksToRun(@NotNull Module module) {
    for (GradleTestTasksProvider provider : GradleTestTasksProvider.EP_NAME.getExtensions()) {
      final List<String> tasks = provider.getTasks(module);
      if(!ContainerUtil.isEmpty(tasks)) {
        return tasks;
      }
    }

    final List<String> result;
    final String externalProjectId = ExternalSystemApiUtil.getExternalProjectId(module);
    if (externalProjectId == null) return ContainerUtil.emptyList();
    final String projectPath = ExternalSystemApiUtil.getExternalProjectPath(module);
    if (projectPath == null) return ContainerUtil.emptyList();
    final ExternalProjectInfo externalProjectInfo =
      ExternalSystemUtil.getExternalProjectInfo(module.getProject(), GradleConstants.SYSTEM_ID, projectPath);
    if (externalProjectInfo == null) return ContainerUtil.emptyList();

    boolean trimSourceSet = false;
    if (StringUtil.endsWith(externalProjectId, ":test") || StringUtil.endsWith(externalProjectId, ":main")) {
      result = TEST_SOURCE_SET_TASKS;
      trimSourceSet = true;
    }
    else {
      final DataNode<ModuleData> moduleNode =
        GradleProjectResolverUtil.findModule(externalProjectInfo.getExternalProjectStructure(), projectPath);
      if (moduleNode == null) return ContainerUtil.emptyList();

      final DataNode<TaskData> taskNode;
      final String sourceSetId = StringUtil.substringAfter(externalProjectId, moduleNode.getData().getExternalName() + ':');
      if (sourceSetId == null) {
        taskNode = ExternalSystemApiUtil.find(
          moduleNode, ProjectKeys.TASK,
          node -> GradleCommonClassNames.GRADLE_API_TASKS_TESTING_TEST.equals(node.getData().getType()) &&
                  StringUtil.equals("test", node.getData().getName()));
      }
      else {
        trimSourceSet = true;
        taskNode = ExternalSystemApiUtil.find(
          moduleNode, ProjectKeys.TASK,
          node -> GradleCommonClassNames.GRADLE_API_TASKS_TESTING_TEST.equals(node.getData().getType()) &&
                  StringUtil.startsWith(sourceSetId, node.getData().getName()));
      }

      if (taskNode == null) return ContainerUtil.emptyList();
      final String taskName = taskNode.getData().getName();
      result = ContainerUtil.list("clean" + StringUtil.capitalize(taskName), taskName);
    }

    final String path;
    if(!externalProjectId.startsWith(":")) {
      path = ":";
    } else {
      final List<String> pathParts = StringUtil.split(externalProjectId, ":");
      if (trimSourceSet && !pathParts.isEmpty()) pathParts.remove(pathParts.size() - 1);
      final String join = StringUtil.join(pathParts, ":");
      path = ":" + join + (!join.isEmpty() ? ":" : "");
    }
    return ContainerUtil.map(result, s -> path + s);
  }
}
