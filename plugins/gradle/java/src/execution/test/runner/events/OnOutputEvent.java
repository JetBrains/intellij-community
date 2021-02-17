// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.gradle.execution.test.runner.events;

import com.intellij.execution.process.ProcessOutputTypes;
import com.intellij.execution.testframework.sm.runner.SMTestProxy;
import com.intellij.execution.testframework.sm.runner.events.TestOutputEvent;
import com.intellij.openapi.externalSystem.model.task.event.ExternalSystemMessageEvent;
import com.intellij.openapi.externalSystem.model.task.event.ExternalSystemProgressEvent;
import com.intellij.openapi.externalSystem.model.task.event.TestOperationDescriptor;
import com.intellij.openapi.util.Key;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.gradle.execution.test.runner.GradleTestsExecutionConsole;

/**
 * @author Vladislav.Soroka
 */
public class OnOutputEvent extends AbstractTestEvent {

  private static final String OUT = "StdOut";
  private static final String ERR = "StdErr";

  public OnOutputEvent(GradleTestsExecutionConsole executionConsole) {
    super(executionConsole);
  }

  @Override
  public void process(@NotNull final TestEventXmlView eventXml) throws TestEventXmlView.XmlParserException {
    final String testId = eventXml.getTestId();
    final String destinationString = eventXml.getTestEventTestDescription();
    final String output = decode(eventXml.getTestEventTest());

    Key destination = OUT.equals(destinationString) ? ProcessOutputTypes.STDOUT : ProcessOutputTypes.STDERR;
    doProcess(testId, output, destination);
  }

  @Override
  public void process(@NotNull ExternalSystemProgressEvent<? extends TestOperationDescriptor> testEvent) {
    if (!(testEvent instanceof ExternalSystemMessageEvent)) {
      return;
    }
    TestOperationDescriptor testDescriptor = testEvent.getDescriptor();

    final String testId = testEvent.getEventId();
    final String description = ((ExternalSystemMessageEvent)testEvent).getDescription();

    if (description == null) {
      doProcess(testId, "", ProcessOutputTypes.STDERR);
    } else if (description.startsWith(OUT)) {
      doProcess(testId, description.substring(OUT.length()), ProcessOutputTypes.STDOUT);
    } else if (description.startsWith(ERR)) {
      doProcess(testId, description.substring(ERR.length()), ProcessOutputTypes.STDERR);
    }
  }

  private void doProcess(String testId, String output, @NotNull Key type) {
    SMTestProxy testProxy = findTestProxy(testId);
    if (testProxy == null) return;

    testProxy.addOutput(output, type);
    getExecutionConsole().getEventPublisher().onTestOutput(testProxy, new TestOutputEvent(testProxy.getName(), output, type == ProcessOutputTypes.STDOUT));
  }
}
