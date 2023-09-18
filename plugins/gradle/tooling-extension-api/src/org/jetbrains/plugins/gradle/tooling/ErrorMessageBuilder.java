// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.gradle.tooling;

import org.gradle.api.Project;
import org.gradle.util.GradleVersion;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Intended to be used only for reporting of custom {@link ModelBuilderService}s unhandled failures.
 * <p>
 * Use {@link MessageReporter} for errors, warnings detected by your {@link ModelBuilderService}.
 *
 * @author Vladislav.Soroka
 * @see MessageBuilder
 * @see MessageReporter
 */
public final class ErrorMessageBuilder {
  @NotNull private final Project myProject;
  @Nullable private final Exception myException;
  @NotNull private final String myGroup;
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

  public ErrorMessageBuilder withDescription(@NotNull String description) {
    myDescription = description;
    return this;
  }

  @ApiStatus.Internal
  public Message buildMessage() {
    return new MessageBuilder()
      .withTitle(getMessageTitle())
      .withText(myDescription != null ? myDescription : "")
      // custom model builders failures often not so critical to the import results and reported as warnings to avoid useless distraction
      .withKind(Message.Kind.WARNING)
      .withException(myException)
      .withGroup(myGroup)
      .withLocation(myProject.getBuildFile().getPath(), 0, 0)
      .build();
  }

  private @NotNull String getMessageTitle() {
    String projectDisplayName = getDisplayName(myProject);
    String title = null;
    if (myException != null) {
      title = getRootCauseMessage(myException);
    }
    if (title == null) {
      title = myDescription != null ? myDescription : myGroup;
    }
    return projectDisplayName + ": " + title;
  }

  @NotNull
  private static String getDisplayName(@NotNull Project project) {
    String projectDisplayName;
    if (GradleVersion.current().getBaseVersion().compareTo(GradleVersion.version("3.3")) < 0) {
      StringBuilder builder = new StringBuilder();
      if (project.getParent() == null && project.getGradle().getParent() == null) {
        builder.append("root project '");
        builder.append(project.getName());
        builder.append('\'');
      }
      else {
        builder.append("project '");
        builder.append(project.getPath());
        builder.append("'");
      }
      projectDisplayName = builder.toString();
    }
    else {
      projectDisplayName = project.getDisplayName();
    }
    return projectDisplayName;
  }

  @Nullable
  private static String getRootCauseMessage(@NotNull Throwable t) {
    Throwable e = t;
    while (true) {
      if (e.getCause() == null) {
        String message = e.getMessage();
        return message == null ? t.getMessage() : message;
      }
      e = e.getCause();
    }
  }
}
