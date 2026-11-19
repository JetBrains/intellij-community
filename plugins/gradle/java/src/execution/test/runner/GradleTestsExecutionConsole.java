// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.execution.test.runner;

import com.intellij.build.BuildViewManager;
import com.intellij.build.events.BuildEventsNls.Message;
import com.intellij.build.events.OutputBuildEvent;
import com.intellij.execution.process.ProcessOutputType;
import com.intellij.execution.testframework.TestConsoleProperties;
import com.intellij.execution.testframework.sm.runner.SMTRunnerEventsListener;
import com.intellij.execution.testframework.sm.runner.SMTestLocator;
import com.intellij.execution.testframework.sm.runner.SMTestProxy;
import com.intellij.execution.testframework.sm.runner.ui.SMTRunnerConsoleView;
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskId;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import org.jetbrains.annotations.ApiStatus.Internal;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.Map;

/**
 * @author Vladislav.Soroka
 */
@Internal
public class GradleTestsExecutionConsole extends SMTRunnerConsoleView {

  private final @NotNull ExternalSystemTaskId myTaskId;
  private final @NotNull SMTRunnerEventsListener myEventPublisher;
  private final @NotNull BuildViewManager myBuildViewManager;

  private final @NotNull Map<String, SMTestProxy> testsMap = new HashMap<>();
  private final @NotNull StringBuilder myBuffer = new StringBuilder();

  private boolean myLastOutputXmlEvent;

  public GradleTestsExecutionConsole(
    @NotNull Project project,
    @NotNull ExternalSystemTaskId taskId,
    @NotNull TestConsoleProperties consoleProperties,
    @NotNull String splitterProperty
  ) {
    super(consoleProperties, splitterProperty);
    myTaskId = taskId;
    myEventPublisher = consoleProperties.getProject().getMessageBus().syncPublisher(SMTRunnerEventsListener.TEST_STATUS);
    myBuildViewManager = project.getService(BuildViewManager.class);
  }

  public @NotNull SMTRunnerEventsListener getEventPublisher() {
    return myEventPublisher;
  }

  public @NotNull Map<String, SMTestProxy> getTestsMap() {
    return testsMap;
  }

  public @NotNull StringBuilder getBuffer() {
    return myBuffer;
  }

  @Override
  public void dispose() {
    testsMap.clear();
    super.dispose();
  }

  public @NotNull SMTestLocator getUrlProvider() {
    return GradleConsoleProperties.GRADLE_TEST_LOCATOR;
  }

  /**
   * Prints the build output into the build tool window.
   */
  public void printToBuildView(@NotNull @Message String text, @NotNull Key<?> outputType) {
    myBuildViewManager.onEvent(myTaskId, OutputBuildEvent.builder(text)
      .withParentId(myTaskId)
      .withOutputType(ProcessOutputType.fromKey(outputType))
      .build());
  }

  public boolean isLastOutputXmlEvent() {
    return myLastOutputXmlEvent;
  }

  public void setLastOutputXmlEvent(boolean lastOutputXmlEvent) {
    myLastOutputXmlEvent = lastOutputXmlEvent;
  }
}
