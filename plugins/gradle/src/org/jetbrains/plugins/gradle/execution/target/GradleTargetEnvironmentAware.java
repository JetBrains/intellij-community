// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.gradle.execution.target;

import com.intellij.execution.target.TargetEnvironment;
import com.intellij.execution.target.TargetEnvironmentAwareRunProfileState;
import com.intellij.execution.target.TargetEnvironmentRequest;
import org.jetbrains.annotations.NotNull;

public interface GradleTargetEnvironmentAware {
  void prepareTargetEnvironmentRequest(@NotNull TargetEnvironmentRequest request,
                                       @NotNull GradleServerEnvironmentSetup environmentSetup,
                                       @NotNull TargetEnvironmentAwareRunProfileState.TargetProgressIndicator progressIndicator);

  void handleCreatedTargetEnvironment(@NotNull TargetEnvironment targetEnvironment,
                                      @NotNull GradleServerEnvironmentSetup environmentSetup,
                                      @NotNull TargetEnvironmentAwareRunProfileState.TargetProgressIndicator progressIndicator);
}
