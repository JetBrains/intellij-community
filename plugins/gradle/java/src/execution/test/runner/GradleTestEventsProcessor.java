// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.execution.test.runner;

import com.intellij.openapi.externalSystem.model.task.event.*;
import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.gradle.execution.test.runner.events.*;

public final class GradleTestEventsProcessor {

  public static void onStatusChange(GradleTestsExecutionConsole console, ExternalSystemProgressEvent<? extends TestOperationDescriptor> event) {
    TestOperationDescriptor descriptor = event.getDescriptor();

    final TestEventType eventType = getEventType(descriptor, event);
    if (eventType == null) {
      return;
    }

    TestEventProcessor testEventProcessor = null;
    switch (eventType) {
      case BEFORE_SUITE:
        testEventProcessor = new BeforeSuiteEventProcessor(console);
        break;
      case BEFORE_TEST:
        testEventProcessor = new BeforeTestEventProcessor(console);
        break;
      case ON_OUTPUT:
        testEventProcessor = new OnOutputEventProcessor(console);
        break;
      case AFTER_TEST:
        testEventProcessor = new AfterTestEventProcessor(console);
        break;
      case AFTER_SUITE:
        testEventProcessor = new AfterSuiteEventProcessor(console);
        break;
      case CONFIGURATION_ERROR:
      case REPORT_LOCATION:
      case UNKNOWN_EVENT:
        break;
    }
    if (testEventProcessor != null) {
      testEventProcessor.process(event);
    }
  }

  private static TestEventType getEventType(@NotNull TestOperationDescriptor descriptor,
                                            @NotNull ExternalSystemProgressEvent event) {
    if (event instanceof ExternalSystemStartEvent) {
      if (StringUtil.isEmpty(descriptor.getMethodName())) {
        return TestEventType.BEFORE_SUITE;
      } else {
        return TestEventType.BEFORE_TEST;
      }
    }
    if (event instanceof ExternalSystemFinishEvent) {
      if (StringUtil.isEmpty(descriptor.getMethodName())) {
        return TestEventType.AFTER_SUITE;
      } else {
        return TestEventType.AFTER_TEST;
      }
    }

    if (event instanceof ExternalSystemStatusEvent) {
      return TestEventType.ON_OUTPUT;
    }
    return null;
  }
}
