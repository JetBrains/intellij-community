// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.gradle.service.execution;

import com.intellij.openapi.externalSystem.service.execution.AbstractExternalSystemTaskConfigurationType;
import com.intellij.openapi.externalSystem.util.ExternalSystemUtil;
import org.jetbrains.plugins.gradle.util.GradleConstants;

public final class GradleExternalTaskConfigurationType extends AbstractExternalSystemTaskConfigurationType {
  public GradleExternalTaskConfigurationType() {
    super(GradleConstants.SYSTEM_ID);
  }

  public static GradleExternalTaskConfigurationType getInstance() {
    return (GradleExternalTaskConfigurationType)ExternalSystemUtil.findConfigurationType(GradleConstants.SYSTEM_ID);
  }
}
