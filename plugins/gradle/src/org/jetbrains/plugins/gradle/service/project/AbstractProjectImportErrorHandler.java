/*
 * Copyright 2000-2013 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.plugins.gradle.service.project;

import com.intellij.openapi.externalSystem.model.ExternalSystemException;
import com.intellij.openapi.externalSystem.model.LocationAwareExternalSystemException;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @author Vladislav.Soroka
 * @since 10/16/13
 */
public abstract class AbstractProjectImportErrorHandler {
  public static final String OPEN_GRADLE_SETTINGS = "Please fix the project's Gradle settings.";
  public static final String SET_UP_HTTP_PROXY =
    "If you are behind an HTTP proxy, please configure the proxy settings either in IDE or Gradle.";
  public static final String UNEXPECTED_ERROR_FILE_BUG = "This is an unexpected error. Please file a bug containing the idea.log file.";
  public static final String FIX_GRADLE_VERSION =
    "Please point to a supported Gradle version in the project's Gradle settings or in the project's Gradle wrapper (if applicable.)";

  public static final Pattern UNSUPPORTED_GRADLE_VERSION_ERROR_PATTERN;
  public static final Pattern MISSING_METHOD_PATTERN;
  public static final Pattern ERROR_LOCATION_PATTERN;
  private static final Pattern ERROR_IN_FILE_PATTERN;

  static {
    UNSUPPORTED_GRADLE_VERSION_ERROR_PATTERN = Pattern.compile("Gradle version .* is required.*");
    MISSING_METHOD_PATTERN = Pattern.compile("org.gradle.api.internal.MissingMethodException: Could not find method (.*?) .*");
    ERROR_LOCATION_PATTERN = Pattern.compile("Build file '(.*)' line: ([\\d]+)");
    ERROR_IN_FILE_PATTERN = Pattern.compile("Build file '(.*)'");
  }

  public static final String EMPTY_LINE = "\n\n";

  @Nullable
  public abstract ExternalSystemException getUserFriendlyError(@NotNull Throwable error,
                                                               @NotNull String projectPath,
                                                               @Nullable String buildFilePath);

  @NotNull
  public Pair<Throwable, String> getRootCauseAndLocation(@NotNull Throwable error) {
    Throwable rootCause = error;
    String location = null;
    while (true) {
      if (location == null) {
        location = getLocationFrom(rootCause);
      }
      if (rootCause.getCause() == null || rootCause.getCause().getMessage() == null) {
        break;
      }
      rootCause = rootCause.getCause();
    }
    //noinspection ConstantConditions
    return Pair.create(rootCause, location);
  }

  @Nullable
  public String getLocationFrom(@NotNull Throwable error) {
    String errorToString = error.toString();
    if (errorToString.contains("LocationAwareException")) {
      // LocationAwareException is never passed, but converted into a PlaceholderException that has the toString value of the original
      // LocationAwareException.
      String location = error.getMessage();
      if (location != null && location.startsWith("Build file '")) {
        // Only the first line contains the location of the error. Discard the rest.
        String[] lines = StringUtil.splitByLines(location);
        return lines.length > 0 ? lines[0] : null;
      }
    }
    return null;
  }

  @NotNull
  public ExternalSystemException createUserFriendlyError(@NotNull String msg, @Nullable String location, @NotNull String... quickFixes) {
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


  @Nullable
  private static Pair<String, Integer> getErrorLocation(@NotNull String location) {
    Matcher matcher = ERROR_LOCATION_PATTERN.matcher(location);
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


  @Nullable
  public String parseMissingMethod(@NotNull String rootCauseText) {
    Matcher matcher = MISSING_METHOD_PATTERN.matcher(rootCauseText);
    return matcher.find() ? matcher.group(1) : null;
  }
}
