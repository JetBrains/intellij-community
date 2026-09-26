// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.service.execution;

import com.intellij.execution.configurations.ConfigurationFactory;
import com.intellij.openapi.externalSystem.model.ProjectSystemId;
import com.intellij.openapi.externalSystem.service.execution.AbstractExternalSystemTaskConfigurationType;
import com.intellij.openapi.externalSystem.util.ExternalSystemUtil;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.gradle.util.GradleConstants;

public final class GradleExternalTaskConfigurationType extends AbstractExternalSystemTaskConfigurationType {
  public GradleExternalTaskConfigurationType() {
    super(GradleConstants.SYSTEM_ID);
  }

  @Override
  public String getHelpTopic() {
    return "reference.dialogs.rundebug.GradleRunConfiguration";
  }

  public static GradleExternalTaskConfigurationType getInstance() {
    return (GradleExternalTaskConfigurationType)ExternalSystemUtil.findConfigurationType(GradleConstants.SYSTEM_ID);
  }

  @Override
  protected @NotNull String getConfigurationFactoryId() {
    return "Gradle";
  }

  @Override
  protected @NotNull GradleRunConfiguration doCreateConfiguration(@NotNull ProjectSystemId externalSystemId,
                                                                  @NotNull Project project,
                                                                  @NotNull ConfigurationFactory factory,
                                                                  @NotNull String name) {
    return new GradleRunConfiguration(project, factory, name);
  }

  @Override
  public boolean isDumbAware() {
    return true;
  }

  @Override
  protected boolean isEditableInDumbMode() {
    return true;
  }
}


