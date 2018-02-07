/*
 * Copyright 2000-2017 JetBrains s.r.o.
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

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.externalSystem.model.ExternalSystemException;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.util.text.StringUtil;
import org.gradle.cli.CommandLineArgumentException;
import org.gradle.tooling.UnsupportedVersionException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.gradle.service.execution.GradleExecutionErrorHandler;
import org.jetbrains.plugins.gradle.service.notification.ApplyGradlePluginCallback;
import org.jetbrains.plugins.gradle.service.notification.GotoSourceNotificationCallback;
import org.jetbrains.plugins.gradle.service.notification.OpenGradleSettingsCallback;

import java.io.FileNotFoundException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.ConnectException;
import java.net.UnknownHostException;

/**
 * @author Vladislav.Soroka
 * @since 10/16/13
 */
public class BaseProjectImportErrorHandler extends AbstractProjectImportErrorHandler {

  private static final Logger LOG = Logger.getInstance(BaseProjectImportErrorHandler.class);

  @NotNull
  @Override
  public ExternalSystemException getUserFriendlyError(@NotNull Throwable error,
                                                      @NotNull String projectPath,
                                                      @Nullable String buildFilePath) {
    GradleExecutionErrorHandler executionErrorHandler = new GradleExecutionErrorHandler(error, projectPath, buildFilePath);
    ExternalSystemException friendlyError = executionErrorHandler.getUserFriendlyError();
    if (friendlyError != null) {
      return friendlyError;
    }

    LOG.info(String.format("Failed to import Gradle project at '%1$s'", projectPath), error);

    if(error instanceof ProcessCanceledException) {
      return new ExternalSystemException("Project build was cancelled");
    }

    Throwable rootCause = executionErrorHandler.getRootCause();
    String location = executionErrorHandler.getLocation();
    if (location == null && !StringUtil.isEmpty(buildFilePath)) {
      location = String.format("Build file: '%1$s'", buildFilePath);
    }

    if (rootCause instanceof UnsupportedVersionException) {
      String msg = "You are using unsupported version of Gradle.";
      msg += ('\n' + FIX_GRADLE_VERSION);
      // Location of build.gradle is useless for this error. Omitting it.
      return createUserFriendlyError(msg, null);
    }

    final String rootCauseMessage = rootCause.getMessage();
    // CommandLineArgumentException class can be loaded by different classloaders
    if (rootCause.getClass().getName().equals(CommandLineArgumentException.class.getName())) {
      if (StringUtil.contains(rootCauseMessage, "Unknown command-line option '--include-build'")) {
        String msg = String.format(
          "Gradle composite build support available for Gradle 3.1 or better version (<a href=\"%s\">Fix Gradle settings</a>)",
          OpenGradleSettingsCallback.ID);
        return createUserFriendlyError(msg, location, OpenGradleSettingsCallback.ID);
      }
    }

    final String rootCauseText = rootCause.toString();
    if (StringUtil.startsWith(rootCauseText, "org.gradle.api.internal.MissingMethodException")) {
      String method = parseMissingMethod(rootCauseText);
      String msg = "Build script error, unsupported Gradle DSL method found: '" + method + "'!";
      msg += (EMPTY_LINE + "Possible causes could be:  ");
      msg += String.format(
        "%s  - you are using Gradle version where the method is absent (<a href=\"%s\">Fix Gradle settings</a>)",
        '\n', OpenGradleSettingsCallback.ID);
      msg += String.format(
        "%s  - you didn't apply Gradle plugin which provides the method (<a href=\"%s\">Apply Gradle plugin</a>)",
        '\n', ApplyGradlePluginCallback.ID);
      msg += String.format(
        "%s  - or there is a mistake in a build script (<a href=\"%s\">Goto source</a>)",
        '\n', GotoSourceNotificationCallback.ID);
      return createUserFriendlyError(
        msg, location, OpenGradleSettingsCallback.ID, ApplyGradlePluginCallback.ID, GotoSourceNotificationCallback.ID);
    }

    if (rootCause instanceof OutOfMemoryError) {
      // The OutOfMemoryError happens in the Gradle daemon process.
      String msg = "Out of memory";
      if (rootCauseMessage != null && !rootCauseMessage.isEmpty()) {
        msg = msg + ": " + rootCauseMessage;
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
      String msg = String.format("Unable to load class '%1$s'.", rootCauseMessage) + EMPTY_LINE +
                   UNEXPECTED_ERROR_FILE_BUG;
      // Location of build.gradle is useless for this error. Omitting it.
      return createUserFriendlyError(msg, null);
    }

    if (rootCause instanceof UnknownHostException) {
      String msg = String.format("Unknown host '%1$s'.", rootCauseMessage) +
                   EMPTY_LINE + "Please ensure the host name is correct. " +
                   SET_UP_HTTP_PROXY;
      // Location of build.gradle is useless for this error. Omitting it.
      return createUserFriendlyError(msg, null);
    }

    if (rootCause instanceof ConnectException) {
      String msg = rootCauseMessage;
      if (msg != null && msg.contains("timed out")) {
        msg += msg.endsWith(".") ? " " : ". ";
        msg += SET_UP_HTTP_PROXY;
        return createUserFriendlyError(msg, null);
      }
    }

    if (rootCause instanceof FileNotFoundException) {
      Throwable errorCause = error.getCause();
      if (errorCause instanceof IllegalArgumentException &&
          GradleExecutionErrorHandler.DOWNLOAD_GRADLE_DISTIBUTION_ERROR_PATTERN.matcher(errorCause.getMessage()).matches()) {
        return createUserFriendlyError(errorCause.getMessage(), null);
      }
    }

    if (rootCause instanceof RuntimeException) {
      String msg = rootCauseMessage;

      if (msg != null && GradleExecutionErrorHandler.UNSUPPORTED_GRADLE_VERSION_ERROR_PATTERN.matcher(msg).matches()) {
        if (!msg.endsWith(".")) {
          msg += ".";
        }
        msg += EMPTY_LINE + OPEN_GRADLE_SETTINGS;
        // Location of build.gradle is useless for this error. Omitting it.
        return createUserFriendlyError(msg, null);
      }
    }

    final String errMessage;
    if (rootCauseMessage == null || ApplicationManager.getApplication().isUnitTestMode()) {
      StringWriter writer = new StringWriter();
      if (rootCauseMessage != null) {
        writer.write(rootCauseMessage + "\n");
      }
      rootCause.printStackTrace(new PrintWriter(writer));
      errMessage = writer.toString();
    }
    else {
      errMessage = rootCauseMessage;
    }
    return createUserFriendlyError(errMessage, location);
  }
}
