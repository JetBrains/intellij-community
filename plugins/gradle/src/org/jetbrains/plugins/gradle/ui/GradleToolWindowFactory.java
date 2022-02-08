// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.ui;

import com.intellij.openapi.externalSystem.service.task.ui.AbstractExternalSystemToolWindowFactory;
import org.jetbrains.plugins.gradle.util.GradleConstants;

final class GradleToolWindowFactory extends AbstractExternalSystemToolWindowFactory {
  GradleToolWindowFactory() {
    super(GradleConstants.SYSTEM_ID);
  }
}
