// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.service.execution;

import com.intellij.openapi.externalSystem.model.ExternalSystemException;
import com.intellij.openapi.externalSystem.model.LocationAwareExternalSystemException;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.gradle.issue.GradleThrowableIssueFailure;

import java.io.FileNotFoundException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @author Vladislav.Soroka
 * @deprecated Use {@link org.jetbrains.plugins.gradle.issue.GradleIssueFailure} for structured Gradle failures.
 * This class is kept only for the remaining Throwable-based import error handling.
 */
@Deprecated
public class GradleExecutionErrorHandler {
  public static final Pattern UNSUPPORTED_GRADLE_VERSION_ERROR_PATTERN;
  public static final Pattern DOWNLOAD_GRADLE_DISTIBUTION_ERROR_PATTERN;
  public static final Pattern MISSING_METHOD_PATTERN;
  public static final Pattern ERROR_LOCATION_IN_FILE_PATTERN;
  public static final Pattern ERROR_IN_FILE_PATTERN;

  static {
    UNSUPPORTED_GRADLE_VERSION_ERROR_PATTERN = Pattern.compile("Gradle version .* is required.*");
    DOWNLOAD_GRADLE_DISTIBUTION_ERROR_PATTERN = Pattern.compile("The specified Gradle distribution .* does not exist.");
    MISSING_METHOD_PATTERN = Pattern.compile("org.gradle.api.internal.MissingMethodException: Could not find method (.*?) .*");
    ERROR_LOCATION_IN_FILE_PATTERN = Pattern.compile("(?:Build|Settings) file '(.*)' line: (\\d+)");
    ERROR_IN_FILE_PATTERN = Pattern.compile("(?:Build|Settings) file '(.*)'");
  }

  private final Throwable myOriginError;
  private final ExternalSystemException myFriendlyError;
  private Pair<Throwable, String> myRootCauseAndLocation;

  public GradleExecutionErrorHandler(@NotNull Throwable error) {
    myOriginError = error;
    myFriendlyError = getUserFriendlyError(error);
  }

  public ExternalSystemException getUserFriendlyError() {
    return myFriendlyError;
  }

  public Throwable getRootCause() {
    if (myRootCauseAndLocation == null) {
      myRootCauseAndLocation = getRootCauseAndLocation(myOriginError);
    }
    return myRootCauseAndLocation.first;
  }

  public String getLocation() {
    if (myRootCauseAndLocation == null) {
      myRootCauseAndLocation = getRootCauseAndLocation(myOriginError);
    }
    return myRootCauseAndLocation.second;
  }

  private @Nullable ExternalSystemException getUserFriendlyError(@NotNull Throwable error) {
    if (error instanceof ExternalSystemException) {
      // This is already a user-friendly error.
      return (ExternalSystemException)error;
    }

    if (myRootCauseAndLocation == null) {
      myRootCauseAndLocation = getRootCauseAndLocation(error);
    }
    if (myRootCauseAndLocation.first instanceof FileNotFoundException) {
      Throwable errorCause = error.getCause();
      if (errorCause instanceof IllegalArgumentException &&
          DOWNLOAD_GRADLE_DISTIBUTION_ERROR_PATTERN.matcher(errorCause.getMessage()).matches()) {
        return createUserFriendlyError(errorCause.getMessage(), null);
      }
    }
    return null;
  }

  public static @NotNull Pair<Throwable, String> getRootCauseAndLocation(@NotNull Throwable error) {
    var failure = new GradleThrowableIssueFailure(error);
    var rootFailure = failure.getRootCause().getThrowable();
    var filePosition = failure.getFilePosition();
    if (filePosition == null) {
      return Pair.create(rootFailure, null);
    }
    var fileLocation = "Build file '" + filePosition.getPath() + "' line: " + filePosition.getStartLine();
    return Pair.create(rootFailure, fileLocation);
  }

  public static @NotNull ExternalSystemException createUserFriendlyError(@NotNull String msg,
                                                                         @Nullable String location,
                                                                         String @NotNull ... quickFixes) {
    String newMsg = msg;
    if (!newMsg.isEmpty() && Character.isLowerCase(newMsg.charAt(0))) {
      // Message starts with lower case letter. Sentences should start with uppercase.
      newMsg = "Cause: " + newMsg;
    }

    if (!StringUtil.isEmpty(location)) {
      Pair<String, Integer> pair = getErrorLocation(location);
      if (pair != null) {
        return new LocationAwareExternalSystemException(newMsg, pair.first, pair.getSecond(), quickFixes);
      }
    }
    return new ExternalSystemException(newMsg, null, quickFixes);
  }

  public static @Nullable Pair<String, Integer> getErrorLocation(@NotNull String location) {
    Matcher matcher = ERROR_LOCATION_IN_FILE_PATTERN.matcher(location);
    if (matcher.matches()) {
      String filePath = matcher.group(1);
      int line = -1;
      try {
        line = Integer.parseInt(matcher.group(2));
      }
      catch (NumberFormatException e) {
        // ignored.
      }
      return Pair.create(filePath, line);
    }

    matcher = ERROR_IN_FILE_PATTERN.matcher(location);
    if (matcher.matches()) {
      String filePath = matcher.group(1);
      return Pair.create(filePath, -1);
    }
    return null;
  }
}
