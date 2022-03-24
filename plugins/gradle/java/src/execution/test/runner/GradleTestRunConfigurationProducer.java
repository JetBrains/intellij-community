// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.gradle.execution.test.runner;

import com.intellij.execution.RunManager;
import com.intellij.execution.RunnerAndConfigurationSettings;
import com.intellij.execution.actions.ConfigurationContext;
import com.intellij.execution.actions.ConfigurationFromContext;
import com.intellij.execution.actions.RunConfigurationProducer;
import com.intellij.execution.configurations.ConfigurationFactory;
import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.externalSystem.model.ProjectKeys;
import com.intellij.openapi.externalSystem.model.ProjectSystemId;
import com.intellij.openapi.externalSystem.model.project.TestData;
import com.intellij.openapi.externalSystem.service.execution.ExternalSystemRunConfiguration;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.util.SmartList;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;
import org.jetbrains.plugins.gradle.execution.GradleRunnerUtil;
import org.jetbrains.plugins.gradle.execution.build.CachedModuleDataFinder;
import org.jetbrains.plugins.gradle.service.execution.GradleExternalTaskConfigurationType;
import org.jetbrains.plugins.gradle.service.execution.GradleRunConfiguration;
import org.jetbrains.plugins.gradle.settings.GradleProjectSettings;
import org.jetbrains.plugins.gradle.settings.TestRunner;
import org.jetbrains.plugins.gradle.util.GradleConstants;
import org.jetbrains.plugins.gradle.util.GradleModuleData;
import org.jetbrains.plugins.gradle.util.TasksToRun;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import static org.jetbrains.plugins.gradle.settings.TestRunner.*;

public abstract class GradleTestRunConfigurationProducer extends RunConfigurationProducer<GradleRunConfiguration> {

  protected static final Logger LOG = Logger.getInstance(GradleTestRunConfigurationProducer.class);

  private TestTasksChooser testTasksChooser = new TestTasksChooser();

  protected GradleTestRunConfigurationProducer() {
    super(true);
  }

  @Override
  public @NotNull ConfigurationFactory getConfigurationFactory() {
    return GradleExternalTaskConfigurationType.getInstance().getFactory();
  }

  @Override
  public boolean isPreferredConfiguration(@NotNull ConfigurationFromContext self, @NotNull ConfigurationFromContext other) {
    return isUsedTestRunners(self.getConfiguration(), CHOOSE_PER_TEST, GRADLE);
  }

  @Override
  public boolean shouldReplace(@NotNull ConfigurationFromContext self, @NotNull ConfigurationFromContext other) {
    return isUsedTestRunners(self.getConfiguration(), GRADLE);
  }

  @Override
  public boolean setupConfigurationFromContext(
    @NotNull GradleRunConfiguration configuration,
    @NotNull ConfigurationContext context,
    @NotNull Ref<PsiElement> sourceElement
  ) {
    if (!GradleConstants.SYSTEM_ID.equals(configuration.getSettings().getExternalSystemId())) return false;

    if (sourceElement.isNull()) return false;
    if (isUsedTestRunners(context, PLATFORM)) return false;
    configuration.setScriptDebugEnabled(false);
    boolean result = doSetupConfigurationFromContext(configuration, context, sourceElement);
    restoreDefaultScriptParametersIfNeeded(configuration, context);
    return result;
  }

  protected abstract boolean doSetupConfigurationFromContext(
    @NotNull GradleRunConfiguration configuration,
    @NotNull ConfigurationContext context,
    @NotNull Ref<PsiElement> sourceElement
  );

  @Override
  public boolean isConfigurationFromContext(@NotNull GradleRunConfiguration configuration, @NotNull ConfigurationContext context) {
    ProjectSystemId externalSystemId = configuration.getSettings().getExternalSystemId();
    return GradleConstants.SYSTEM_ID.equals(externalSystemId) &&
           isUsedTestRunners(configuration, CHOOSE_PER_TEST, GRADLE) &&
           doIsConfigurationFromContext(configuration, context);
  }

  protected abstract boolean doIsConfigurationFromContext(
    @NotNull GradleRunConfiguration configuration,
    @NotNull ConfigurationContext context
  );

  @Override
  public void onFirstRun(
    @NotNull ConfigurationFromContext configuration,
    @NotNull ConfigurationContext context,
    @NotNull Runnable startRunnable
  ) {
    restoreDefaultScriptParametersIfNeeded(configuration.getConfiguration(), context);
    startRunnable.run();
  }

  protected void restoreDefaultScriptParametersIfNeeded(
    @NotNull RunConfiguration configuration,
    @NotNull ConfigurationContext context
  ) {
    RunnerAndConfigurationSettings template = context.getRunManager().getConfigurationTemplate(getConfigurationFactory());
    final RunConfiguration original = template.getConfiguration();
    if (original instanceof ExternalSystemRunConfiguration
        && configuration instanceof ExternalSystemRunConfiguration) {
      ExternalSystemRunConfiguration originalRC = (ExternalSystemRunConfiguration)original;
      ExternalSystemRunConfiguration configurationRC = (ExternalSystemRunConfiguration)configuration;
      String currentParams = configurationRC.getSettings().getScriptParameters();
      String defaultParams = originalRC.getSettings().getScriptParameters();

      if (!StringUtil.isEmptyOrSpaces(defaultParams)) {
        if (!StringUtil.isEmptyOrSpaces(currentParams)) {
          configurationRC.getSettings().setScriptParameters(currentParams + " " + defaultParams);
        }
        else {
          configurationRC.getSettings().setScriptParameters(defaultParams);
        }
      }
    }
  }

  protected static @Nullable String resolveProjectPath(@NotNull Module module) {
    GradleModuleData gradleModuleData = CachedModuleDataFinder.getGradleModuleData(module);
    if (gradleModuleData == null) return null;
    boolean isGradleProjectDirUsedToRunTasks = gradleModuleData.getDirectoryToRunTask().equals(gradleModuleData.getGradleProjectDir());
    if (!isGradleProjectDirUsedToRunTasks) {
      return gradleModuleData.getDirectoryToRunTask();
    }
    return GradleRunnerUtil.resolveProjectPath(module);
  }

  protected TestTasksChooser getTestTasksChooser() {
    return testTasksChooser;
  }

  @TestOnly
  public void setTestTasksChooser(TestTasksChooser testTasksChooser) {
    this.testTasksChooser = testTasksChooser;
  }

  /**
   * Finds any of possible tasks to run tests for specified source
   *
   * @param source  is a file or directory for find in source set
   * @param project is a project with the source
   * @return any of possible tasks to run tests for specified source
   */
  @NotNull
  public static TasksToRun findTestsTaskToRun(@NotNull VirtualFile source, @NotNull Project project) {
    List<TasksToRun> tasksToRun = findAllTestsTaskToRun(source, project);
    if (tasksToRun.isEmpty()) return TasksToRun.EMPTY;
    return tasksToRun.get(0);
  }

  /**
   * Finds all of possible tasks to run tests for specified source
   *
   * @param source  is a file or directory for find in source set
   * @param project is a project with the source
   * @return all of possible tasks to run tests for specified source
   */
  @NotNull
  public static List<TasksToRun> findAllTestsTaskToRun(@NotNull VirtualFile source, @NotNull Project project) {
    String sourcePath = source.getPath();
    ProjectFileIndex projectFileIndex = ProjectFileIndex.SERVICE.getInstance(project);
    Module module = projectFileIndex.getModuleForFile(source);
    if (module == null) return Collections.emptyList();
    List<TasksToRun> testTasks = new ArrayList<>();
    for (GradleTestTasksProvider provider : GradleTestTasksProvider.EP_NAME.getExtensions()) {
      List<String> tasks = provider.getTasks(module, source);
      if (!ContainerUtil.isEmpty(tasks)) {
        String testName = StringUtil.join(tasks, " ");
        testTasks.add(new TasksToRun.Impl(testName, tasks));
      }
    }
    GradleModuleData gradleModuleData = CachedModuleDataFinder.getGradleModuleData(module);
    if (gradleModuleData == null) return testTasks;

    for (TestData testData : gradleModuleData.findAll(ProjectKeys.TEST)) {
      Set<String> sourceFolders = testData.getSourceFolders();
      for (String sourceFolder : sourceFolders) {
        if (FileUtil.isAncestor(sourceFolder, sourcePath, false)) {
          String testTaskSimpleName = testData.getTestName();
          List<String> tasks = new SmartList<>(gradleModuleData.getTaskPath(testTaskSimpleName, true));
          testTasks.add(new TasksToRun.Impl(testTaskSimpleName, tasks));
        }
      }
    }
    return testTasks;
  }

  private static boolean isUsedTestRunners(@NotNull RunConfiguration configuration, TestRunner @NotNull ... runners) {
    return configuration instanceof GradleRunConfiguration &&
           isUsedTestRunners((GradleRunConfiguration)configuration, runners);
  }

  private static boolean isUsedTestRunners(@NotNull GradleRunConfiguration configuration, TestRunner @NotNull ... runners) {
    Project project = configuration.getProject();
    String externalProjectPath = configuration.getSettings().getExternalProjectPath();
    return isUsedTestRunners(project, externalProjectPath, runners);
  }

  private static boolean isUsedTestRunners(@NotNull ConfigurationContext context, TestRunner @NotNull ... runners) {
    Project project = context.getProject();
    Module module = context.getModule();
    return project != null && module != null &&
           isUsedTestRunners(project, resolveProjectPath(module), runners);
  }

  private static boolean isUsedTestRunners(
    @NotNull Project project,
    @Nullable String externalProjectPath,
    TestRunner @NotNull ... runners
  ) {
    TestRunner testRunner = GradleProjectSettings.getTestRunner(project, externalProjectPath);
    return ContainerUtil.exists(runners, it -> it.equals(testRunner));
  }

  protected static void setUniqueNameIfNeeded(@NotNull Project project, @NotNull GradleRunConfiguration configuration) {
    RunManager runManager = RunManager.getInstance(project);
    runManager.setUniqueNameIfNeeded(configuration);
  }
}
