// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.gradle.execution.target;

import com.intellij.execution.target.TargetEnvironment;
import com.intellij.execution.target.TargetEnvironmentRequest;
import com.intellij.execution.target.TargetProgressIndicator;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

/**
 * Extension point to customize Gradle Server execution on remote targets.
 * <br/><br/>
 * To execute Gradle on WSL/SSH/other target, an additional server is used.
 * This extension point allows customizing the way this server is launched.
 */
@ApiStatus.Internal
public interface GradleTargetEnvironmentAware {
  /**
   * Modify request before target environment is created
   */
  void prepareTargetEnvironmentRequest(@NotNull TargetEnvironmentRequest request,
                                       @NotNull GradleServerEnvironmentSetup environmentSetup,
                                       @NotNull TargetProgressIndicator progressIndicator);

  /**
   * Modify created target environment
   */
  void handleCreatedTargetEnvironment(@NotNull TargetEnvironment targetEnvironment,
                                      @NotNull GradleServerEnvironmentSetup environmentSetup,
                                      @NotNull TargetProgressIndicator progressIndicator);
}
