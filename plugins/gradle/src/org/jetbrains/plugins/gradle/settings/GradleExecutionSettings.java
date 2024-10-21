// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.gradle.settings;

import com.intellij.openapi.externalSystem.model.settings.ExternalSystemExecutionSettings;
import com.intellij.util.ObjectUtils;
import com.intellij.util.SystemProperties;
import com.intellij.util.execution.ParametersListUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.gradle.service.execution.GradleRunConfiguration;

import java.util.Objects;

public class GradleExecutionSettings extends ExternalSystemExecutionSettings {

  private static final @NotNull String USE_VERBOSE_GRADLE_API_KEY = "gradle.api.verbose";
  private static final boolean USE_VERBOSE_GRADLE_API_DEFAULT = false;

  private final @NotNull GradleExecutionWorkspace myExecutionWorkspace;

  private @Nullable String myGradleHome = null;

  private @Nullable String myServiceDirectory = null;
  private boolean myIsOfflineWork = false;

  private @NotNull DistributionType myDistributionType = DistributionType.BUNDLED;
  private @Nullable String wrapperPropertyFile = null;

  private @Nullable String myJavaHome = null;
  private @Nullable String myIdeProjectPath = null;
  private boolean resolveModulePerSourceSet = true;
  private boolean useQualifiedModuleNames = false;
  private boolean delegatedBuild = true;
  private boolean downloadSources = false;
  private boolean isParallelModelFetch = false;

  private boolean myBuiltInTestEventsUsed = false;

  /**
   * @deprecated use default constructor instead
   */
  @Deprecated
  public GradleExecutionSettings(
    @Nullable String gradleHome,
    @Nullable String serviceDirectory,
    @NotNull DistributionType distributionType,
    boolean isOfflineWork
  ) {
    this();

    setGradleHome(gradleHome);
    setServiceDirectory(serviceDirectory);
    setDistributionType(distributionType);
    setOfflineWork(isOfflineWork);
  }

  /**
   * @deprecated use default constructor instead
   */
  @Deprecated
  public GradleExecutionSettings(
    @Nullable String gradleHome,
    @Nullable String serviceDirectory,
    @NotNull DistributionType distributionType,
    @Nullable String daemonVmOptions,
    boolean isOfflineWork
  ) {
    this(gradleHome, serviceDirectory, distributionType, isOfflineWork);
    if (daemonVmOptions != null) {
      withVmOptions(ParametersListUtil.parse(daemonVmOptions));
    }
  }

  public GradleExecutionSettings() {
    myExecutionWorkspace = new GradleExecutionWorkspace();

    setVerboseProcessing(SystemProperties.getBooleanProperty(USE_VERBOSE_GRADLE_API_KEY, USE_VERBOSE_GRADLE_API_DEFAULT));
  }

  public GradleExecutionSettings(@NotNull GradleExecutionSettings settings) {
    super(settings);

    myExecutionWorkspace = settings.myExecutionWorkspace;

    myGradleHome = settings.myGradleHome;

    myServiceDirectory = settings.myServiceDirectory;
    myIsOfflineWork = settings.myIsOfflineWork;

    myDistributionType = settings.myDistributionType;
    wrapperPropertyFile = settings.wrapperPropertyFile;

    myJavaHome = settings.myJavaHome;
    myIdeProjectPath = settings.myIdeProjectPath;
    resolveModulePerSourceSet = settings.resolveModulePerSourceSet;
    useQualifiedModuleNames = settings.useQualifiedModuleNames;
    delegatedBuild = settings.delegatedBuild;
    downloadSources = settings.downloadSources;
    isParallelModelFetch = settings.isParallelModelFetch;

    myBuiltInTestEventsUsed = settings.myBuiltInTestEventsUsed;
  }

  public void setIdeProjectPath(@Nullable String ideProjectPath) {
    myIdeProjectPath = ideProjectPath;
  }

  @Nullable
  public String getIdeProjectPath() {
    return myIdeProjectPath;
  }

  @Nullable
  public String getGradleHome() {
    return myGradleHome;
  }

  public void setGradleHome(@Nullable String gradleHome) {
    myGradleHome = gradleHome;
  }

  @Nullable
  public String getServiceDirectory() {
    return myServiceDirectory;
  }

  public void setServiceDirectory(@Nullable String serviceDirectory) {
    myServiceDirectory = serviceDirectory;
  }

  @Nullable
  public String getJavaHome() {
    return myJavaHome;
  }

  public void setJavaHome(@Nullable String javaHome) {
    myJavaHome = javaHome;
  }

  public boolean isOfflineWork() {
    return myIsOfflineWork;
  }

  public void setOfflineWork(boolean offlineWork) {
    myIsOfflineWork = offlineWork;
  }

  public boolean isResolveModulePerSourceSet() {
    return resolveModulePerSourceSet;
  }

  public void setResolveModulePerSourceSet(boolean resolveModulePerSourceSet) {
    this.resolveModulePerSourceSet = resolveModulePerSourceSet;
  }

  public boolean isUseQualifiedModuleNames() {
    return useQualifiedModuleNames;
  }

  public void setUseQualifiedModuleNames(boolean useQualifiedModuleNames) {
    this.useQualifiedModuleNames = useQualifiedModuleNames;
  }

  public boolean isDelegatedBuild() {
    return delegatedBuild;
  }

  public void setDelegatedBuild(boolean delegatedBuild) {
    this.delegatedBuild = delegatedBuild;
  }

  @Nullable
  public String getWrapperPropertyFile() {
    return wrapperPropertyFile;
  }

  public void setWrapperPropertyFile(@Nullable String wrapperPropertyFile) {
    this.wrapperPropertyFile = wrapperPropertyFile;
  }

  @NotNull
  public DistributionType getDistributionType() {
    return myDistributionType;
  }

  public void setDistributionType(@NotNull DistributionType distributionType) {
    myDistributionType = distributionType;
  }

  @NotNull
  public GradleExecutionWorkspace getExecutionWorkspace() {
    return myExecutionWorkspace;
  }

  public boolean isDebugAllEnabled() {
    var value = getUserData(GradleRunConfiguration.DEBUG_ALL_KEY);
    return ObjectUtils.chooseNotNull(value, false);
  }

  public boolean isRunAsTest() {
    var value = getUserData(GradleRunConfiguration.RUN_AS_TEST_KEY);
    return ObjectUtils.chooseNotNull(value, false);
  }

  public void setRunAsTest(boolean isRunAsTest) {
    putUserData(GradleRunConfiguration.RUN_AS_TEST_KEY, isRunAsTest);
  }

  public boolean isTestTaskRerun() {
    var value = getUserData(GradleRunConfiguration.IS_TEST_TASK_RERUN_KEY);
    return ObjectUtils.chooseNotNull(value, false);
  }

  public boolean isBuiltInTestEventsUsed() {
    return myBuiltInTestEventsUsed;
  }

  public void setBuiltInTestEventsUsed(boolean isBuiltInTestEventsUsed) {
    myBuiltInTestEventsUsed = isBuiltInTestEventsUsed;
  }

  public boolean isDownloadSources() {
    return downloadSources;
  }

  public void setDownloadSources(boolean downloadSources) {
    this.downloadSources = downloadSources;
  }

  public boolean isParallelModelFetch() {
    return isParallelModelFetch;
  }

  public void setParallelModelFetch(boolean parallelModelFetch) {
    isParallelModelFetch = parallelModelFetch;
  }

  @Override
  public int hashCode() {
    int result = super.hashCode();
    result = 31 * result + (myGradleHome != null ? myGradleHome.hashCode() : 0);
    result = 31 * result + (myServiceDirectory != null ? myServiceDirectory.hashCode() : 0);
    result = 31 * result + myDistributionType.hashCode();
    result = 31 * result + (myJavaHome != null ? myJavaHome.hashCode() : 0);
    return result;
  }

  @Override
  public boolean equals(Object o) {
    if (!super.equals(o)) return false;
    GradleExecutionSettings that = (GradleExecutionSettings)o;
    if (myDistributionType != that.myDistributionType) return false;
    if (!Objects.equals(myGradleHome, that.myGradleHome)) return false;
    if (!Objects.equals(myJavaHome, that.myJavaHome)) return false;
    if (!Objects.equals(myServiceDirectory, that.myServiceDirectory)) return false;
    return true;
  }

  @Override
  public String toString() {
    return "home: " + myGradleHome + ", distributionType: " + myDistributionType;
  }
}
