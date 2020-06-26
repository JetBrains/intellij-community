// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.gradle.tooling;

import org.gradle.api.Project;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.PrintWriter;
import java.io.StringWriter;

/**
 * @author Vladislav.Soroka
 */
public final class ErrorMessageBuilder {
  public static final String GROUP_TAG = "<ij_msg_gr>";
  public static final String NAV_TAG = "<ij_nav>";
  public static final String EOL_TAG = "<eol>";

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

  public String build() {
    String group = myGroup.replaceAll("\r\n|\n\r|\n|\r", " ");
    final File projectBuildFile = myProject.getBuildFile();
    return (
      GROUP_TAG + group + GROUP_TAG +
      (projectBuildFile != null ? (NAV_TAG + projectBuildFile.getPath() + NAV_TAG) : "") +
      (
        "<i>" +
        "<b>" + myProject + ((myDescription != null) ? ": " + myDescription : "") + "</b>" +
        (myException != null ? "\nDetails: " + getErrorMessage(myException) : "") +
        "</i>"
      ).replaceAll("\r\n|\n\r|\n|\r", EOL_TAG)
    );
  }


  private static String getErrorMessage(@NotNull Throwable e) {
    if (Boolean.valueOf(System.getProperty("idea.tooling.debug"))) {
      StringWriter sw = new StringWriter();
      e.printStackTrace(new PrintWriter(sw));
      return sw.toString();
    }

    StringBuilder buf = new StringBuilder();
    Throwable cause = e;
    while (cause != null) {
      if (buf.length() != 0) {
        buf.append("\nCaused by: ");
      }
      buf.append(cause.getClass().getName()).append(": ").append(cause.getMessage());
      cause = cause.getCause();
    }
    return buf.toString();
  }
}
