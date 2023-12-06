// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.gradle.tooling;

import org.gradle.api.Project;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @deprecated implement {@link ModelBuilderService#reportErrorMessage} instead
 *
 * @author Vladislav.Soroka
 */
@Deprecated
public final class ErrorMessageBuilder {
  @NotNull private final Project myProject;
  @Nullable private final Exception myException;
  @NotNull private final String myGroup;
  @Nullable private String myTitle;
  @Nullable private String myDescription;

  private ErrorMessageBuilder(@NotNull Project project, @Nullable Exception exception, @NotNull String group) {
    myProject = project;
    myException = exception;
    myGroup = group;
  }

  public static ErrorMessageBuilder create(@NotNull Project project, @NotNull String group) {
    return new ErrorMessageBuilder(project, null, group);
  }

  public static ErrorMessageBuilder create(@NotNull Project project, @Nullable Exception exception, @NotNull String group) {
    return new ErrorMessageBuilder(project, exception, group);
  }

  public ErrorMessageBuilder withTitle(@NotNull String title) {
    myTitle = title;
    return this;
  }

  public ErrorMessageBuilder withDescription(@NotNull String description) {
    myDescription = description;
    return this;
  }

  @ApiStatus.Internal
  public Message buildMessage() {
    return new DefaultMessageBuilder()
      .withTitle(myTitle)
      .withText(myDescription)
      // custom model builders failures often not so critical to the import results and reported as warnings to avoid useless distraction
      .withKind(Message.Kind.WARNING)
      .withException(myException)
      .withGroup(myGroup)
      .withProject(myProject)
      .build();
  }
}
