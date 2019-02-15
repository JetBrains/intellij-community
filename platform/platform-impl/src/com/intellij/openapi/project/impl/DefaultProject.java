// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.project.impl;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author peter
 */
class DefaultProject extends ProjectImpl {
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

  @Nullable
  @Override
  protected String measureTokenNamePrefix() {
    return "default project ";
  }
}
