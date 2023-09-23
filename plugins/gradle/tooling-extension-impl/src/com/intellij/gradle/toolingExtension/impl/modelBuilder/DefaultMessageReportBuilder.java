// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.gradle.toolingExtension.impl.modelBuilder;

import org.gradle.api.Project;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.gradle.tooling.Message;
import org.jetbrains.plugins.gradle.tooling.MessageBuilder;
import org.jetbrains.plugins.gradle.tooling.MessageReportBuilder;
import org.jetbrains.plugins.gradle.tooling.MessageReporter;

@ApiStatus.Internal
public class DefaultMessageReportBuilder implements MessageReportBuilder {

  private final @NotNull MessageReporter myMessageReporter;
  private final @NotNull MessageBuilder myMessageBuilder = new MessageBuilder();

  public DefaultMessageReportBuilder(@NotNull MessageReporter reporter) {
    myMessageReporter = reporter;
  }

  @Override
  public @NotNull MessageReportBuilder withTitle(String title) {
    myMessageBuilder.withTitle(title);
    return this;
  }

  @Override
  public @NotNull MessageReportBuilder withText(String text) {
    myMessageBuilder.withText(text);
    return this;
  }

  @Override
  public @NotNull MessageReportBuilder withKind(Message.Kind kind) {
    myMessageBuilder.withKind(kind);
    return this;
  }

  @Override
  public @NotNull MessageReportBuilder withGroup(String group) {
    myMessageBuilder.withGroup(group);
    return this;
  }

  @Override
  public @NotNull MessageReportBuilder withException(Exception e) {
    myMessageBuilder.withException(e);
    return this;
  }

  @Override
  public @NotNull MessageReportBuilder withLocation(String filePath, int line, int column) {
    myMessageBuilder.withLocation(filePath, line, column);
    return this;
  }

  @Override
  public void reportMessage(@NotNull Project project) {
    myMessageReporter.reportMessage(project, myMessageBuilder.build());
  }
}
