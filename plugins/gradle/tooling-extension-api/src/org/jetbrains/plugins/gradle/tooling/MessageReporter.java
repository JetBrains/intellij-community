// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.tooling;

import org.gradle.api.Project;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

public interface MessageReporter {

  @ApiStatus.Internal String MODEL_BUILDER_SERVICE_MESSAGE_PREFIX = "ModelBuilderService message: ";

  @NotNull MessageReportBuilder createMessage();

  void reportMessage(@NotNull Project project, @NotNull Message message);
}
