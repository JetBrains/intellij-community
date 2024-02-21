// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.gradle.toolingExtension.impl.modelAction;

import com.intellij.gradle.toolingExtension.impl.telemetry.GradleOpenTelemetry;
import org.gradle.tooling.BuildController;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import com.intellij.gradle.toolingExtension.impl.modelSerialization.ModelConverter;
import com.intellij.gradle.toolingExtension.impl.modelSerialization.ToolingSerializerConverter;

@ApiStatus.Internal
public final class GradleModelFetchActionWithCustomSerializer extends GradleModelFetchAction {

  @Override
  protected @NotNull ModelConverter getToolingModelConverter(@NotNull BuildController controller, @NotNull GradleOpenTelemetry telemetry) {
    return new ToolingSerializerConverter(controller, telemetry);
  }
}
