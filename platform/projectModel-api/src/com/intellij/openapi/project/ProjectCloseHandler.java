// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.project;

import org.jetbrains.annotations.NotNull;

/**
 * Register via {@code com.intellij.projectCloseHandler} extension point.
 */
public interface ProjectCloseHandler {
  /**
   * Checks whether the project can be closed.
   *
   * @param project project to check
   * @return true or false
   */
  boolean canClose(@NotNull Project project);
}
