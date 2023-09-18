// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.tooling;

import org.gradle.api.Project;
import org.jetbrains.annotations.NotNull;

public interface MessageReportBuilder {

  @NotNull MessageReportBuilder withTitle(String title);

  @NotNull MessageReportBuilder withText(String text);

  @NotNull MessageReportBuilder withKind(Message.Kind kind);

  @NotNull MessageReportBuilder withGroup(String group);

  @NotNull MessageReportBuilder withException(Exception e);

  @NotNull MessageReportBuilder withLocation(String filePath, int line, int column);

  void reportMessage(@NotNull Project project);
}
