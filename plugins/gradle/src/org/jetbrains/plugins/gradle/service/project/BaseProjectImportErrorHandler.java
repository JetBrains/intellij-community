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

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.externalSystem.model.ExternalSystemException;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.text.StringUtil;
import org.gradle.tooling.UnsupportedVersionException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.ConnectException;
import java.net.UnknownHostException;

/**
 * @author Vladislav.Soroka
 * @since 10/16/13
 */
public class BaseProjectImportErrorHandler extends AbstractProjectImportErrorHandler {

  private static final Logger LOG = Logger.getInstance("#" + BaseProjectImportErrorHandler.class.getName());

  @NotNull
  @Override
  public ExternalSystemException getUserFriendlyError(@NotNull Throwable error,
                                                      @NotNull String projectPath,
                                                      @Nullable String buildFilePath) {
    if (error instanceof ExternalSystemException) {
      // This is already a user-friendly error.
      return (ExternalSystemException)error;
    }

    LOG.info(String.format("Failed to import Gradle project at '%1$s'", projectPath), error);

    Pair<Throwable, String> rootCauseAndLocation = getRootCauseAndLocation(error);

    Throwable rootCause = rootCauseAndLocation.getFirst();

    String location = rootCauseAndLocation.getSecond();
    if (location == null && !StringUtil.isEmpty(buildFilePath)) {
      location = String.format("Build file: '%1$s'", buildFilePath);
    }

    if (rootCause instanceof UnsupportedVersionException) {
      String msg = "You are using unsupported version of Gradle.";
      msg += ('\n' + FIX_GRADLE_VERSION);
      // Location of build.gradle is useless for this error. Omitting it.
      return createUserFriendlyError(msg, null);
    }

    final String rootCauseText = rootCause.toString();
    if (StringUtil.startsWith(rootCauseText, "org.gradle.api.internal.MissingMethodException")) {
      String method = parseMissingMethod(rootCauseText);
      String msg = "Build script error, unsupported Gradle DSL method found: '" + method + "'!";
      msg += (EMPTY_LINE + "Possible causes could be:  ");
      msg += ('\n' + "  - you are using Gradle version where the method is absent ");
      msg += ('\n' + "  - you didn't apply Gradle plugin which provides the method");
      msg += ('\n' + "  - or there is a mistake in a build script");
      return createUserFriendlyError(msg, location);
    }

    if (rootCause instanceof OutOfMemoryError) {
      // The OutOfMemoryError happens in the Gradle daemon process.
      String originalMessage = rootCause.getMessage();
      String msg = "Out of memory";
      if (originalMessage != null && !originalMessage.isEmpty()) {
        msg = msg + ": " + originalMessage;
      }
      if (msg.endsWith("Java heap space")) {
        msg += ". Configure Gradle memory settings using '-Xmx' JVM option (e.g. '-Xmx2048m'.)";
      }
      else if (!msg.endsWith(".")) {
        msg += ".";
      }
      msg += EMPTY_LINE + OPEN_GRADLE_SETTINGS;
      // Location of build.gradle is useless for this error. Omitting it.
      return createUserFriendlyError(msg, null);
    }

    if (rootCause instanceof ClassNotFoundException) {
      String msg = String.format("Unable to load class '%1$s'.", rootCause.getMessage()) + EMPTY_LINE +
                   UNEXPECTED_ERROR_FILE_BUG;
      // Location of build.gradle is useless for this error. Omitting it.
      return createUserFriendlyError(msg, null);
    }

    if (rootCause instanceof UnknownHostException) {
      String msg = String.format("Unknown host '%1$s'.", rootCause.getMessage()) +
                   EMPTY_LINE + "Please ensure the host name is correct. " +
                   SET_UP_HTTP_PROXY;
      // Location of build.gradle is useless for this error. Omitting it.
      return createUserFriendlyError(msg, null);
    }

    if (rootCause instanceof ConnectException) {
      String msg = rootCause.getMessage();
      if (msg != null && msg.contains("timed out")) {
        msg += msg.endsWith(".") ? " " : ". ";
        msg += SET_UP_HTTP_PROXY;
        return createUserFriendlyError(msg, null);
      }
    }

    if (rootCause instanceof RuntimeException) {
      String msg = rootCause.getMessage();

      if (msg != null && UNSUPPORTED_GRADLE_VERSION_ERROR_PATTERN.matcher(msg).matches()) {
        if (!msg.endsWith(".")) {
          msg += ".";
        }
        msg += EMPTY_LINE + OPEN_GRADLE_SETTINGS;
        // Location of build.gradle is useless for this error. Omitting it.
        return createUserFriendlyError(msg, null);
      }
    }

    final String errMessage;
    if (rootCause.getMessage() == null) {
      StringWriter writer = new StringWriter();
      rootCause.printStackTrace(new PrintWriter(writer));
      errMessage = writer.toString();
    }
    else {
      errMessage = rootCause.getMessage();
    }
    return createUserFriendlyError(errMessage, location);
  }
}
