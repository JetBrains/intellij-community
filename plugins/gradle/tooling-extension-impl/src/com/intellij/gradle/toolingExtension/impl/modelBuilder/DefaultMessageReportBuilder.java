// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.gradle.toolingExtension.impl.modelBuilder;

import org.gradle.api.Project;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.gradle.tooling.DefaultMessageBuilder;
import org.jetbrains.plugins.gradle.tooling.Message;
import org.jetbrains.plugins.gradle.tooling.MessageReportBuilder;
import org.jetbrains.plugins.gradle.tooling.MessageReporter;
import org.jetbrains.plugins.gradle.tooling.ModelBuilderService;

@ApiStatus.Internal
public class DefaultMessageReportBuilder implements MessageReportBuilder {

  private final @NotNull MessageReporter myMessageReporter;

  private @Nullable String myTitle = null;
  private @Nullable String myText = null;
  private @Nullable String myGroup = null;
  private @Nullable Exception myException = null;
  private @Nullable Message.Kind myKind = null;
  private @Nullable Message.FilePosition myFilePosition = null;

  private boolean myInternal = false;

  public DefaultMessageReportBuilder(@NotNull MessageReporter reporter) {
    myMessageReporter = reporter;
  }

  @Override
  public @NotNull MessageReportBuilder withTitle(String title) {
    myTitle = title;
    return this;
  }

  @Override
  public @NotNull MessageReportBuilder withText(String text) {
    myText = text;
    return this;
  }

  @Override
  public @NotNull MessageReportBuilder withKind(Message.Kind kind) {
    myKind = kind;
    return this;
  }

  @Override
  public @NotNull MessageReportBuilder withGroup(String group) {
    myGroup = group;
    return this;
  }

  @Override
  public @NotNull MessageReportBuilder withGroup(ModelBuilderService group) {
    myGroup = group.getClass().getName();
    return this;
  }

  @Override
  public @NotNull MessageReportBuilder withException(Exception e) {
    myException = e;
    return this;
  }

  @Override
  public @NotNull MessageReportBuilder withStackTrace() {
    myException = new IllegalStateException();
    return this;
  }

  @Override
  public @NotNull MessageReportBuilder withLocation(String filePath, int line, int column) {
    myFilePosition = new Message.FilePosition(filePath, line, column);
    return this;
  }

  @Override
  public @NotNull MessageReportBuilder withInternal() {
    myInternal = true;
    return this;
  }

  @Override
  public void reportMessage(@NotNull Project project) {
    myMessageReporter.reportMessage(project, new DefaultMessageBuilder()
      .withTitle(myTitle)
      .withText(myText)
      .withKind(myKind)
      .withGroup(myGroup)
      .withException(myException)
      .withLocation(myFilePosition)
      .withProject(project)
      .withInternal(myInternal)
      .build()
    );
  }
}
