// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.project.impl;

import com.intellij.openapi.components.ComponentConfig;
import org.jetbrains.annotations.NotNull;

/**
 * @author peter
 */
final class DefaultProject extends ProjectImpl {
  private static final String TEMPLATE_PROJECT_NAME = "Default (Template) Project";

  DefaultProject(@NotNull String filePath) {
    super(filePath, TEMPLATE_PROJECT_NAME);
  }

  @Override
  public boolean isDefault() {
    return true;
  }

  @Override
  public boolean isInitialized() {
    return true; // no startup activities, never opened
  }

  @NotNull
  @Override
  protected String activityNamePrefix() {
    return "default project ";
  }

  @Override
  protected boolean isComponentSuitable(@NotNull ComponentConfig componentConfig) {
    return super.isComponentSuitable(componentConfig) && componentConfig.isLoadForDefaultProject();
  }
}
