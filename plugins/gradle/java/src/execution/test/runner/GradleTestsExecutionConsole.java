// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.gradle.execution.test.runner;

import com.intellij.build.BuildViewManager;
import com.intellij.build.BuildViewSettingsProvider;
import com.intellij.build.events.impl.OutputBuildEventImpl;
import com.intellij.execution.Platform;
import com.intellij.execution.testframework.TestConsoleProperties;
import com.intellij.execution.testframework.sm.runner.SMTRunnerEventsListener;
import com.intellij.execution.testframework.sm.runner.SMTestLocator;
import com.intellij.execution.testframework.sm.runner.SMTestProxy;
import com.intellij.execution.testframework.sm.runner.ui.SMTRunnerConsoleView;
import com.intellij.execution.ui.ConsoleViewContentType;
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskId;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;

/**
 * @author Vladislav.Soroka
 */
public class GradleTestsExecutionConsole extends SMTRunnerConsoleView implements BuildViewSettingsProvider {
  private final Map<String, SMTestProxy> testsMap = new HashMap<>();
  private final StringBuilder myBuffer = new StringBuilder();
  private final SMTRunnerEventsListener myEventPublisher;
  private final @Nullable BuildViewManager myBuildViewManager;
  private final @Nullable ExternalSystemTaskId myTaskId;
  private boolean lastMessageWasEmptyLine;

  public GradleTestsExecutionConsole(TestConsoleProperties consoleProperties,
                                     @Nullable String splitterProperty) {
    this(null, null, consoleProperties, splitterProperty);
  }
  public GradleTestsExecutionConsole(@Nullable Project project,
                                     @Nullable ExternalSystemTaskId taskId,
                                     @NotNull TestConsoleProperties consoleProperties,
                                     @Nullable String splitterProperty) {
    super(consoleProperties, splitterProperty);
    myEventPublisher = consoleProperties.getProject().getMessageBus().syncPublisher(SMTRunnerEventsListener.TEST_STATUS);
    myBuildViewManager = project != null ? project.getService(BuildViewManager.class) : null;
    myTaskId = taskId;
  }

  public SMTRunnerEventsListener getEventPublisher() {
    return myEventPublisher;
  }

  public Map<String, SMTestProxy> getTestsMap() {
    return testsMap;
  }

  public StringBuilder getBuffer() {
    return myBuffer;
  }

  @Override
  public void dispose() {
    testsMap.clear();
    super.dispose();
  }

  public SMTestLocator getUrlProvider() {
    return GradleConsoleProperties.GRADLE_TEST_LOCATOR;
  }

  @Override
  public boolean isExecutionViewHidden() {
    return true;
  }

  @Override
  public void print(@NotNull String s, @NotNull ConsoleViewContentType contentType) {
    if (detectUnwantedEmptyLine(s)) return;
    super.print(s, contentType);
    if (myBuildViewManager != null && myTaskId != null) {
      OutputBuildEventImpl outputBuildEvent = new OutputBuildEventImpl(myTaskId, s, contentType != ConsoleViewContentType.ERROR_OUTPUT); //NON-NLS
      myBuildViewManager.onEvent(myTaskId, outputBuildEvent);
    }
  }

  // IJ Gradle test runner xml events protocol produces many unwanted empty strings
  // this is a workaround to avoid the trash in the console
  private boolean detectUnwantedEmptyLine(@NotNull String s) {
    if (Platform.current().lineSeparator.equals(s)) {
      if (lastMessageWasEmptyLine) return true;
      lastMessageWasEmptyLine = true;
    }
    else {
      lastMessageWasEmptyLine = false;
    }
    return false;
  }
}
