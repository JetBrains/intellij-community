// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.execution.test.runner.events;

import com.intellij.openapi.externalSystem.model.task.event.ExternalSystemProgressEvent;
import com.intellij.openapi.externalSystem.model.task.event.TestOperationDescriptor;
import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.gradle.execution.test.runner.GradleTestsExecutionConsole;

public abstract class AbstractBeforeTestEventProcessor extends AbstractTestEventProcessor {
  public AbstractBeforeTestEventProcessor(GradleTestsExecutionConsole executionConsole) {
    super(executionConsole);
  }

  @Override
  public void process(@NotNull ExternalSystemProgressEvent<? extends TestOperationDescriptor> testEvent) {
    var testDescriptor = testEvent.getDescriptor();
    var testId = testEvent.getEventId();
    var parentTestId = testEvent.getParentEventId();
    var suiteName = StringUtil.notNullize(testDescriptor.getSuiteName());
    var fqClassName = StringUtil.notNullize(testDescriptor.getClassName());
    var methodName = testDescriptor.getMethodName();
    var displayName = testDescriptor.getDisplayName();

    doProcess(testId, parentTestId, suiteName, fqClassName, methodName, displayName);
  }

  protected abstract void doProcess(
    @NotNull String testId,
    @Nullable String parentTestId,
    @NotNull String suiteName,
    @NotNull String fqClassName,
    @Nullable String methodName,
    @NotNull String displayName
  );
}
