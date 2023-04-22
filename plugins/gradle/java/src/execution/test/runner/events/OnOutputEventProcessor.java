// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.gradle.execution.test.runner.events;

import com.intellij.execution.process.ProcessOutputTypes;
import com.intellij.execution.testframework.sm.runner.events.TestOutputEvent;
import com.intellij.openapi.externalSystem.model.task.event.ExternalSystemMessageEvent;
import com.intellij.openapi.externalSystem.model.task.event.ExternalSystemProgressEvent;
import com.intellij.openapi.externalSystem.model.task.event.TestOperationDescriptor;
import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.gradle.execution.test.runner.GradleTestsExecutionConsole;

/**
 * @author Vladislav.Soroka
 */
public class OnOutputEventProcessor extends AbstractTestEventProcessor {

  public OnOutputEventProcessor(GradleTestsExecutionConsole executionConsole) {
    super(executionConsole);
  }

  @Override
  public void process(@NotNull TestEventXmlView eventXml) throws TestEventXmlView.XmlParserException {
    var testId = eventXml.getTestId();
    var output = GradleXmlTestEventConverter.decode(eventXml.getTestEventTest());
    var isStdOut = "StdOut".equals(eventXml.getTestEventTestDescription());

    doProcess(testId, output, isStdOut);
  }

  @Override
  public void process(@NotNull ExternalSystemProgressEvent<? extends TestOperationDescriptor> testEvent) {
    var messageEvent = (ExternalSystemMessageEvent<? extends TestOperationDescriptor>)testEvent;
    var parentTestId = messageEvent.getParentEventId();
    var isStdOut = messageEvent.isStdOut();
    var message = StringUtil.notNullize(messageEvent.getMessage());

    doProcess(parentTestId, message, isStdOut);
  }

  private void doProcess(@Nullable String parentTestId, @NotNull String message, boolean isStdOut) {
    var testProxy = findParentTestProxy(parentTestId);
    var name = testProxy.getName();
    var event = new TestOutputEvent(name, message, isStdOut);
    var destination = isStdOut ? ProcessOutputTypes.STDOUT : ProcessOutputTypes.STDERR;

    testProxy.addOutput(message, destination);
    getExecutionConsole().getEventPublisher().onTestOutput(testProxy, event);
  }
}
