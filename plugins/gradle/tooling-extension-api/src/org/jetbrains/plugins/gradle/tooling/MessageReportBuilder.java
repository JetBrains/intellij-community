// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.tooling;

import org.gradle.api.Project;
import org.jetbrains.annotations.CheckReturnValue;
import org.jetbrains.annotations.NotNull;

public interface MessageReportBuilder {

  @CheckReturnValue
  @NotNull MessageReportBuilder withTitle(String title);

  @CheckReturnValue
  @NotNull MessageReportBuilder withText(String text);

  @CheckReturnValue
  @NotNull MessageReportBuilder withKind(Message.Kind kind);

  @CheckReturnValue
  @NotNull MessageReportBuilder withGroup(String group);

  @CheckReturnValue
  @NotNull MessageReportBuilder withException(Exception e);

  @CheckReturnValue
  @NotNull MessageReportBuilder withStackTrace();

  @CheckReturnValue
  @NotNull MessageReportBuilder withLocation(String filePath, int line, int column);

  void reportMessage(@NotNull Project project);
}
