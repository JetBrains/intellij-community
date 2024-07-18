// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.gradle.settings;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.externalSystem.service.execution.ExternalSystemJdkUtil;
import com.intellij.openapi.externalSystem.settings.ExternalProjectSettings;
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.util.ObjectUtils;
import com.intellij.util.SmartList;
import com.intellij.util.xmlb.annotations.*;
import org.gradle.util.GradleVersion;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.gradle.model.data.BuildParticipant;
import org.jetbrains.plugins.gradle.service.GradleInstallationManager;
import org.jetbrains.plugins.gradle.util.GradleEnvironment;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * {@link GradleProjectSettings} holds settings for the linked gradle project.
 */
@SuppressWarnings("unused")
public class GradleProjectSettings extends ExternalProjectSettings {
  private static final @NotNull Logger LOG = Logger.getInstance(GradleProjectSettings.class);

  public static final boolean DEFAULT_DELEGATE = true;
  public static final @NotNull TestRunner DEFAULT_TEST_RUNNER = TestRunner.GRADLE;

  private @Nullable String myGradleJvm;
  private @Nullable DistributionType myDistributionType;
  private @Nullable String myGradleHome;
  private boolean disableWrapperSourceDistributionNotification;
  private boolean resolveModulePerSourceSet;
  private boolean resolveExternalAnnotations;
  private @Nullable CompositeBuild myCompositeBuild;

  private @NotNull Boolean delegatedBuild;
  private @NotNull TestRunner testRunner;

  public GradleProjectSettings(@NotNull String externalProjectPath) {
    this();
    this.setExternalProjectPath(externalProjectPath);
  }

  public GradleProjectSettings() {
    myGradleJvm = ExternalSystemJdkUtil.USE_PROJECT_JDK;
    myDistributionType = DistributionType.DEFAULT_WRAPPED;
    myGradleHome = null;
    disableWrapperSourceDistributionNotification = false;
    resolveModulePerSourceSet = true;
    resolveExternalAnnotations = false;
    delegatedBuild = DEFAULT_DELEGATE;
    testRunner = DEFAULT_TEST_RUNNER;
    setupHeadlessProjectSettings();
  }

  private void setupHeadlessProjectSettings() {
    var distributionType = GradleEnvironment.Headless.GRADLE_DISTRIBUTION_TYPE;
    if (distributionType != null) {
      myDistributionType = DistributionType.valueOf(distributionType);
    }
    var gradleHome = GradleEnvironment.Headless.GRADLE_HOME;
    if (gradleHome != null) {
      myGradleHome = gradleHome;
    }
  }

  public @Nullable @NlsSafe String getGradleJvm() {
    return myGradleJvm;
  }

  public void setGradleJvm(@Nullable String gradleJvm) {
    myGradleJvm = gradleJvm;
  }

  public @Nullable @NlsSafe String getGradleHome() {
    return myGradleHome;
  }

  public void setGradleHome(@Nullable String gradleHome) {
    myGradleHome = gradleHome;
  }

  public @Nullable DistributionType getDistributionType() {
    return myDistributionType;
  }

  public void setDistributionType(@Nullable DistributionType distributionType) {
    myDistributionType = distributionType;
  }

  public boolean isDisableWrapperSourceDistributionNotification() {
    return disableWrapperSourceDistributionNotification;
  }

  public void setDisableWrapperSourceDistributionNotification(boolean disableWrapperSourceDistributionNotification) {
    this.disableWrapperSourceDistributionNotification = disableWrapperSourceDistributionNotification;
  }

  public boolean isResolveModulePerSourceSet() {
    return resolveModulePerSourceSet;
  }

  public void setResolveModulePerSourceSet(boolean useIdeModulePerSourceSet) {
    this.resolveModulePerSourceSet = useIdeModulePerSourceSet;
  }

  public boolean isResolveExternalAnnotations() {
    return resolveExternalAnnotations;
  }

  public void setResolveExternalAnnotations(boolean resolveExternalAnnotations) {
    this.resolveExternalAnnotations = resolveExternalAnnotations;
  }

  @OptionTag(tag = "compositeConfiguration", nameAttribute = "")
  public @Nullable CompositeBuild getCompositeBuild() {
    return myCompositeBuild;
  }

  public void setCompositeBuild(@Nullable CompositeBuild compositeBuild) {
    myCompositeBuild = compositeBuild;
  }

  /**
   * @return Build/run mode for the gradle project
   */
  @Transient
  public boolean getDelegatedBuild() {
    return ObjectUtils.notNull(delegatedBuild, DEFAULT_DELEGATE);
  }

  public void setDelegatedBuild(@NotNull Boolean state) {
    this.delegatedBuild = state;
  }

  // For backward compatibility
  @OptionTag("delegatedBuild")
  public @Nullable Boolean getDirectDelegatedBuild() {
    return delegatedBuild;
  }

  public void setDirectDelegatedBuild(@Nullable Boolean state) {
    delegatedBuild = ObjectUtils.notNull(state, DEFAULT_DELEGATE);
  }

  public static boolean isDelegatedBuildEnabled(@NotNull Project project, @Nullable String gradleProjectPath) {
    GradleProjectSettings projectSettings = gradleProjectPath == null
                                            ? null : GradleSettings.getInstance(project).getLinkedProjectSettings(gradleProjectPath);
    if (projectSettings == null) return false;

    return projectSettings.getDelegatedBuild();
  }

  public static boolean isDelegatedBuildEnabled(@NotNull Module module) {
    return isDelegatedBuildEnabled(module.getProject(), ExternalSystemApiUtil.getExternalRootProjectPath(module));
  }

  /**
   * @return test runner option.
   */
  @Transient
  public @NotNull TestRunner getTestRunner() {
    return ObjectUtils.notNull(testRunner, DEFAULT_TEST_RUNNER);
  }

  public void setTestRunner(@NotNull TestRunner testRunner) {
    if (LOG.isDebugEnabled()) {
      if (testRunner != TestRunner.GRADLE) {
        LOG.debug(String.format("Gradle test runner sets to %s", testRunner), new Throwable());
      }
    }
    this.testRunner = testRunner;
  }

  // For backward compatibility
  @OptionTag("testRunner")
  public @Nullable TestRunner getDirectTestRunner() {
    return testRunner;
  }

  public void setDirectTestRunner(@Nullable TestRunner testRunner) {
    this.testRunner = ObjectUtils.notNull(testRunner, DEFAULT_TEST_RUNNER);
  }

  public static @NotNull TestRunner getTestRunner(@NotNull Project project, @Nullable String gradleProjectPath) {
    if (gradleProjectPath == null) {
      return TestRunner.PLATFORM;
    }
    var settings = GradleSettings.getInstance(project);
    var projectSettings = settings.getLinkedProjectSettings(gradleProjectPath);
    if (projectSettings == null) {
      return TestRunner.PLATFORM;
    }
    return projectSettings.getTestRunner();
  }

  public static @NotNull TestRunner getTestRunner(@NotNull Module module) {
    return getTestRunner(module.getProject(), ExternalSystemApiUtil.getExternalRootProjectPath(module));
  }

  public @NotNull GradleVersion resolveGradleVersion() {
    GradleVersion version = GradleInstallationManager.guessGradleVersion(this);
    return Optional.ofNullable(version).orElseGet(GradleVersion::current);
  }

  public @NotNull GradleProjectSettings withQualifiedModuleNames() {
    setUseQualifiedModuleNames(true);
    return this;
  }

  @Override
  public @NotNull GradleProjectSettings clone() {
    GradleProjectSettings result = new GradleProjectSettings();
    copyTo(result);
    result.myGradleJvm = myGradleJvm;
    result.myDistributionType = myDistributionType;
    result.myGradleHome = myGradleHome;
    result.disableWrapperSourceDistributionNotification = disableWrapperSourceDistributionNotification;
    result.resolveModulePerSourceSet = resolveModulePerSourceSet;
    result.resolveExternalAnnotations = resolveExternalAnnotations;
    result.myCompositeBuild = myCompositeBuild != null ? myCompositeBuild.copy() : null;

    result.delegatedBuild = delegatedBuild;
    result.testRunner = testRunner;
    return result;
  }

  @Tag("compositeBuild")
  public static class CompositeBuild {
    private @Nullable CompositeDefinitionSource myCompositeDefinitionSource;
    private List<BuildParticipant> myCompositeParticipants = new SmartList<>();

    @Attribute
    public @Nullable CompositeDefinitionSource getCompositeDefinitionSource() {
      return myCompositeDefinitionSource;
    }

    public void setCompositeDefinitionSource(@Nullable CompositeDefinitionSource compositeDefinitionSource) {
      myCompositeDefinitionSource = compositeDefinitionSource;
    }

    @XCollection(propertyElementName = "builds", elementName = "build")
    public @NotNull List<BuildParticipant> getCompositeParticipants() {
      return myCompositeParticipants;
    }

    public void setCompositeParticipants(@Nullable List<? extends BuildParticipant> compositeParticipants) {
      myCompositeParticipants = compositeParticipants == null ? new SmartList<>() : new ArrayList<>(compositeParticipants);
    }

    public @NotNull CompositeBuild copy() {
      CompositeBuild result = new CompositeBuild();
      result.myCompositeParticipants = new ArrayList<>();
      for (BuildParticipant participant : myCompositeParticipants) {
        result.myCompositeParticipants.add(participant.copy());
      }
      result.myCompositeDefinitionSource = myCompositeDefinitionSource;
      return result;
    }
  }

  public static class GradleDistributionInfo {

    private final @NotNull DistributionType type;

    private final @NotNull GradleVersion gradleVersion;

    private final @Nullable String gradleHome;

    public GradleDistributionInfo(@NotNull DistributionType type, @NotNull GradleVersion gradleVersion, @Nullable String gradleHome) {
      this.type = type;
      this.gradleVersion = gradleVersion;
      this.gradleHome = gradleHome;
    }

    public @NotNull DistributionType getType() {
      return type;
    }

    public @NotNull GradleVersion getGradleVersion() {
      return gradleVersion;
    }

    public @Nullable String getGradleHome() {
      return gradleHome;
    }
  }
}
