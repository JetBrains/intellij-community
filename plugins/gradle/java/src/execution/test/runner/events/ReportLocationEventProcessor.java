// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.execution.test.runner.events;

import com.intellij.openapi.externalSystem.model.task.event.ExternalSystemProgressEvent;
import com.intellij.openapi.externalSystem.model.task.event.TestOperationDescriptor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.gradle.execution.test.runner.GradleTestsExecutionConsole;

import java.io.File;

/**
 * @author Vladislav.Soroka
 */
public class ReportLocationEventProcessor extends AbstractTestEventProcessor {

  public ReportLocationEventProcessor(GradleTestsExecutionConsole consoleManager) {
    super(consoleManager);
  }

  @Override
  public void process(final @NotNull TestEventXmlView eventXml) throws TestEventXmlView.XmlParserException {
    final String testReport = eventXml.getEventTestReport();
    getProperties().setGradleTestReport(new File(testReport));
  }

  @Override
  public void process(@NotNull ExternalSystemProgressEvent<? extends TestOperationDescriptor> testEvent) {
    // TODO not yet implemented
  }
}
