// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.execution.test.runner.events;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.externalSystem.model.task.event.*;
import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.gradle.execution.test.runner.GradleTestsExecutionConsole;

public final class GradleTestEventsProcessor {

  private static final Logger LOG = Logger.getInstance("com.intellij.openapi.externalSystem.event-processing");

  public static void onStatusChange(
    @NotNull GradleTestsExecutionConsole console,
    @NotNull ExternalSystemProgressEvent<? extends TestOperationDescriptor> event
  ) {
    var eventProcessor = createEventProcessor(console, event);
    if (eventProcessor != null) {
      eventProcessor.process(event);
    }
  }

  private static @Nullable TestEventProcessor createEventProcessor(
    @NotNull GradleTestsExecutionConsole console,
    @NotNull ExternalSystemProgressEvent<? extends TestOperationDescriptor> event
  ) {
    var descriptor = event.getDescriptor();
    if (event instanceof ExternalSystemStartEvent) {
      if (StringUtil.isEmpty(descriptor.getMethodName()) || isNewParametrizedTest(descriptor)) {
        return new BeforeSuiteEventProcessor(console);
      }
      return new BeforeTestEventProcessor(console);
    }
    if (event instanceof ExternalSystemFinishEvent) {
      if (StringUtil.isEmpty(descriptor.getMethodName()) || isNewParametrizedTest(descriptor)) {
        return new AfterSuiteEventProcessor(console);
      }
      return new AfterTestEventProcessor(console);
    }
    if (event instanceof ExternalSystemMessageEvent) {
      return new OnOutputEventProcessor(console);
    }
    LOG.warn("Undefined progress event " + event.getClass().getSimpleName() + " " + event);
    return null;
  }

  private static boolean isNewParametrizedTest(@NotNull TestOperationDescriptor descriptor) {
    String className = descriptor.getClassName();
    String suiteName = descriptor.getSuiteName();
    String methodName = descriptor.getMethodName();
    return className != null && suiteName != null && suiteName.equals(methodName);
  }
}
