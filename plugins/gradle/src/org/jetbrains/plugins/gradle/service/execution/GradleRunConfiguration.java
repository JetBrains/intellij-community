// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.service.execution;

import com.intellij.execution.Executor;
import com.intellij.execution.configurations.ConfigurationFactory;
import com.intellij.execution.configurations.LocatableRunConfigurationOptions;
import com.intellij.execution.target.LanguageRuntimeType;
import com.intellij.execution.target.TargetEnvironmentAwareRunProfile;
import com.intellij.execution.target.TargetEnvironmentConfiguration;
import com.intellij.execution.testframework.sm.runner.SMRunnerConsolePropertiesProvider;
import com.intellij.execution.testframework.sm.runner.SMTRunnerConsoleProperties;
import com.intellij.openapi.externalSystem.service.execution.ExternalSystemRunConfiguration;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.util.containers.ContainerUtil;
import org.jdom.Element;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.gradle.GradleIdeManager;
import org.jetbrains.plugins.gradle.execution.target.GradleRuntimeType;
import org.jetbrains.plugins.gradle.util.GradleConstants;
import org.jetbrains.plugins.gradle.util.cmd.node.GradleCommandLine;

import static org.jetbrains.plugins.gradle.service.execution.GradleCommandLineUtil.getTestPatterns;

public class GradleRunConfiguration
  extends ExternalSystemRunConfiguration
  implements SMRunnerConsolePropertiesProvider,
             TargetEnvironmentAwareRunProfile {

  private static final String DEBUG_FLAG_NAME = "GradleScriptDebugEnabled";
  private static final String FORCE_TEST_NAME = "ForceTestExec";

  private static final String DEBUG_ALL_NAME = "DebugAllEnabled";
  private static final String RUN_AS_TEST_NAME = "RunAsTest";

  private static final String PROFILING_SUPPORTED_NAME = "GradleProfilingSupported";
  private static final String COVERAGE_SUPPORTED_NAME = "GradleCoverageSupported";

  public static final Key<Boolean> DEBUG_ALL_KEY = Key.create("DEBUG_ALL_TASKS");
  public static final Key<Boolean> RUN_AS_TEST_KEY = Key.create("RUN_AS_TEST");
  public static final Key<Boolean> IS_TEST_TASK_RERUN_KEY = Key.create("IS_TEST_TASK_RERUN");

  private boolean isDebugAllEnabled = false;
  private boolean isRunAsTest = false;

  /**
   * Determines if the profiler should support this run configuration.
   * @see com.intellij.gradle.profiler.GradleProfilerStarterExtension
   * This will cause the profiling action to be hidden in the IDE unless another JavaProfilerStarterExtension accepts the run configuration.
   */
  private boolean isProfilingSupported = true;
  /**
   * Determines if the CoverageEngine should support this run configuration.
   * @see org.jetbrains.plugins.gradle.execution.test.runner.GradleCoverageExtension
   * This will cause the coverage action to be hidden in the IDE unless another JavaCoverageEngineExtension accepts the run configuration.
   */
  private boolean isCoverageSupported = true;

  public GradleRunConfiguration(Project project, ConfigurationFactory factory, String name) {
    super(GradleConstants.SYSTEM_ID, project, factory, name);
    setDebugServerProcess(true);
    setReattachDebugProcess(true);
  }

  public boolean isDebugAllEnabled() {
    return isDebugAllEnabled;
  }

  public void setDebugAllEnabled(boolean debugAllEnabled) {
    isDebugAllEnabled = debugAllEnabled;
    putUserData(DEBUG_ALL_KEY, debugAllEnabled);
  }

  public boolean isRunAsTest() {
    return isRunAsTest;
  }

  public void setRunAsTest(boolean runAsTest) {
    isRunAsTest = runAsTest;
    putUserData(RUN_AS_TEST_KEY, runAsTest);
    putUserData(IS_TEST_TASK_RERUN_KEY, runAsTest);
  }


  public boolean isProfilingSupported() {
    return isProfilingSupported;
  }

  public void setProfilingSupported(boolean profilingSupported) {
    this.isProfilingSupported = profilingSupported;
  }

  public boolean isCoverageSupported() {
    return isCoverageSupported;
  }

  public void setCoverageSupported(boolean coverageSupported) {
    this.isCoverageSupported = coverageSupported;
  }

  public @NotNull String getRawCommandLine() {
    return getCommandLine().getText();
  }

  public void setRawCommandLine(@NotNull String commandLine) {
    setCommandLine(GradleCommandLine.parse(commandLine));
  }

  public void setCommandLine(@NotNull GradleCommandLine commandLine) {
    getSettings().setTaskNames(commandLine.getTasks().getTokens());
    getSettings().setScriptParameters(commandLine.getOptions().getText());
  }

  public @NotNull GradleCommandLine getCommandLine() {
    return GradleCommandLineUtil.parseCommandLine(
      getSettings().getTaskNames(),
      getSettings().getScriptParameters()
    );
  }

  @ApiStatus.Internal
  @Override
  public @NotNull LocatableRunConfigurationOptions getOptions() {
    return super.getOptions();
  }

  @Override
  public void readExternal(@NotNull Element element) throws InvalidDataException {
    super.readExternal(element);

    // migration
    readExternalBoolean(element, DEBUG_FLAG_NAME, this::setDebugServerProcess);
    readExternalBoolean(element, FORCE_TEST_NAME, this::setRunAsTest);

    readExternalBoolean(element, DEBUG_ALL_NAME, this::setDebugAllEnabled);
    if (!readExternalBoolean(element, RUN_AS_TEST_NAME, this::setRunAsTest)) {
      var tasks = getCommandLine().getTasks();
      var isRunAsTest = ContainerUtil.exists(tasks, it -> !getTestPatterns(it).isEmpty());
      setRunAsTest(isRunAsTest);
    }

    readExternalBoolean(element, PROFILING_SUPPORTED_NAME, this::setProfilingSupported);
    readExternalBoolean(element, COVERAGE_SUPPORTED_NAME, this::setCoverageSupported);
  }

  @Override
  public void writeExternal(@NotNull Element element) throws WriteExternalException {
    super.writeExternal(element);
    writeExternalBoolean(element, DEBUG_ALL_NAME, isDebugAllEnabled());
    writeExternalBoolean(element, RUN_AS_TEST_NAME, isRunAsTest());
    writeExternalBoolean(element, PROFILING_SUPPORTED_NAME, isProfilingSupported());
    writeExternalBoolean(element, COVERAGE_SUPPORTED_NAME, isCoverageSupported());
  }

  @Override
  public @NotNull SMTRunnerConsoleProperties createTestConsoleProperties(@NotNull Executor executor) {
    return GradleIdeManager.getInstance().createTestConsoleProperties(getProject(), executor, this);
  }

  @Override
  public boolean canRunOn(@NotNull TargetEnvironmentConfiguration target) {
    return true;
  }

  @Override
  public @Nullable LanguageRuntimeType<?> getDefaultLanguageRuntimeType() {
    return LanguageRuntimeType.EXTENSION_NAME.findExtension(GradleRuntimeType.class);
  }

  @Override
  public @Nullable String getDefaultTargetName() {
    return getOptions().getRemoteTarget();
  }

  @Override
  public void setDefaultTargetName(@Nullable String targetName) {
    getOptions().setRemoteTarget(targetName);
  }
}
