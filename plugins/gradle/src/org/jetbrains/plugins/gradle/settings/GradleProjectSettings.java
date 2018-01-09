/*
 * Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */
package org.jetbrains.plugins.gradle.settings;

import com.intellij.openapi.externalSystem.service.execution.ExternalSystemJdkUtil;
import com.intellij.openapi.externalSystem.settings.ExternalProjectSettings;
import com.intellij.util.SmartList;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.xmlb.annotations.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.gradle.model.data.BuildParticipant;

import java.util.List;

/**
 * @author Denis Zhdanov
 * @since 4/24/13 11:57 AM
 */
public class GradleProjectSettings extends ExternalProjectSettings {

  @Nullable private String myGradleHome;
  @Nullable private String myGradleJvm = ExternalSystemJdkUtil.USE_PROJECT_JDK;
  @Nullable private DistributionType distributionType;
  private boolean disableWrapperSourceDistributionNotification;
  private boolean resolveModulePerSourceSet = true;
  @Nullable private CompositeBuild myCompositeBuild;

  private boolean storeProjectFilesExternally = false;

  @Nullable
  public String getGradleHome() {
    return myGradleHome;
  }

  public void setGradleHome(@Nullable String gradleHome) {
    myGradleHome = gradleHome;
  }

  @Nullable
  public String getGradleJvm() {
    return myGradleJvm;
  }

  public void setGradleJvm(@Nullable String gradleJvm) {
    myGradleJvm = gradleJvm;
  }

  @Nullable
  public DistributionType getDistributionType() {
    return distributionType;
  }

  public void setDistributionType(@Nullable DistributionType distributionType) {
    this.distributionType = distributionType;
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

  @OptionTag(tag = "compositeConfiguration", nameAttribute = "")
  @Nullable
  public CompositeBuild getCompositeBuild() {
    return myCompositeBuild;
  }

  public void setCompositeBuild(@Nullable CompositeBuild compositeBuild) {
    myCompositeBuild = compositeBuild;
  }

  @NotNull
  @Override
  public ExternalProjectSettings clone() {
    GradleProjectSettings result = new GradleProjectSettings();
    copyTo(result);
    result.myGradleHome = myGradleHome;
    result.myGradleJvm = myGradleJvm;
    result.distributionType = distributionType;
    result.disableWrapperSourceDistributionNotification = disableWrapperSourceDistributionNotification;
    result.resolveModulePerSourceSet = resolveModulePerSourceSet;
    result.myCompositeBuild = myCompositeBuild != null ? myCompositeBuild.copy() : null;
    return result;
  }

  @Transient
  public boolean isStoreProjectFilesExternally() {
    return storeProjectFilesExternally;
  }

  public void setStoreProjectFilesExternally(boolean value) {
    storeProjectFilesExternally = value;
  }

  @Tag("compositeBuild")
  public static class CompositeBuild {
    @Nullable private CompositeDefinitionSource myCompositeDefinitionSource;
    private List<BuildParticipant> myCompositeParticipants = new SmartList<>();

    @Attribute
    @Nullable
    public CompositeDefinitionSource getCompositeDefinitionSource() {
      return myCompositeDefinitionSource;
    }

    public void setCompositeDefinitionSource(@Nullable CompositeDefinitionSource compositeDefinitionSource) {
      myCompositeDefinitionSource = compositeDefinitionSource;
    }

    @XCollection(propertyElementName = "builds", elementName = "build")
    @NotNull
    public List<BuildParticipant> getCompositeParticipants() {
      return myCompositeParticipants;
    }

    public void setCompositeParticipants(List<BuildParticipant> compositeParticipants) {
      myCompositeParticipants = compositeParticipants == null ? new SmartList<>() : ContainerUtil.newArrayList(compositeParticipants);
    }

    @NotNull
    public CompositeBuild copy() {
      CompositeBuild result = new CompositeBuild();
      result.myCompositeParticipants = ContainerUtil.newArrayList();
      for (BuildParticipant participant : myCompositeParticipants) {
        result.myCompositeParticipants.add(participant.copy());
      }
      result.myCompositeDefinitionSource = myCompositeDefinitionSource;
      return result;
    }
  }
}
