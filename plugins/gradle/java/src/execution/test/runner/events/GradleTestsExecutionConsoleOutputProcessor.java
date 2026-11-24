// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.execution.test.runner.events;

import com.intellij.build.BuildViewSettingsProvider;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.execution.ui.ConsoleViewContentType;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.ApiStatus.Internal;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.gradle.execution.test.runner.GradleTestsExecutionConsole;

/**
 * Created by eugene.petrenko@gmail.com
 */
@Internal
public final class GradleTestsExecutionConsoleOutputProcessor {
  private static final Logger LOG = Logger.getInstance(GradleTestsExecutionConsoleOutputProcessor.class);
  private static final String LOG_EOL = "<ijLogEol/>";
  private static final String LOG_START = "<ijLog>";
  private static final String LOG_END = "</ijLog>";

  public static void onOutput(
    @NotNull GradleTestsExecutionConsole executionConsole,
    @NotNull BuildViewSettingsProvider consoleSettings,
    @NotNull ProcessHandler processHandler,
    @NotNull String text,
    @NotNull Key<?> processOutputType
  ) {
    switch (getXmlEventOrOutput(executionConsole, text)) {
      case XmlEventOrOutput.Output output -> {
        if (consoleSettings.isSingleBuildConsoleView()) {
          processHandler.notifyTextAvailable(output.text, processOutputType);
        }
        else {
          executionConsole.print(output.text, ConsoleViewContentType.getConsoleViewType(processOutputType));
          executionConsole.printToBuildView(output.text, processOutputType);
        }
      }
      case XmlEventOrOutput.XmlEvent xmlEvent -> {
        try {
          var xml = new TestEventXPPXmlView(xmlEvent.xml);
          var eventType = TestEventType.fromValue(xml.getTestEventType());
          var testEventProcessor = createTestEventProcessor(eventType, executionConsole);
          if (testEventProcessor != null) {
            testEventProcessor.process(xml);
          }
        }
        catch (TestEventXmlView.XmlParserException | NumberFormatException e) {
          LOG.error("Gradle test events parser error", e);
        }
      }
      case null -> {
      }
    }
  }

  private static @Nullable TestEventProcessor createTestEventProcessor(
    @NotNull TestEventType eventType,
    @NotNull GradleTestsExecutionConsole executionConsole
  ) {
    return switch (eventType) {
      case CONFIGURATION_ERROR -> new ConfigurationErrorEventProcessor(executionConsole);
      case REPORT_LOCATION -> new ReportLocationEventProcessor(executionConsole);
      case BEFORE_TEST -> new BeforeTestEventProcessor(executionConsole);
      case ON_OUTPUT -> new OnOutputEventProcessor(executionConsole);
      case AFTER_TEST -> new AfterTestEventProcessor(executionConsole);
      case BEFORE_SUITE -> new BeforeSuiteEventProcessor(executionConsole);
      case AFTER_SUITE -> new AfterSuiteEventProcessor(executionConsole);
      default -> null;
    };
  }

  private static @Nullable XmlEventOrOutput getXmlEventOrOutput(
    @NotNull GradleTestsExecutionConsole executionConsole,
    @NotNull String text
  ) {
    // IJ Gradle test runner prints each xml event on a separate line,
    // the line separator after the event isn't a part of the build output
    if (executionConsole.isLastOutputXmlEvent()) {
      executionConsole.setLastOutputXmlEvent(false);
      if (text.equals("\n") || text.equals("\r\n")) return null;
    }

    var consoleBuffer = executionConsole.getBuffer();
    String eventMessage = null;
    String trimmedText = text.trim();
    if (StringUtil.endsWith(trimmedText, LOG_EOL)) {
      consoleBuffer.append(StringUtil.trimEnd(trimmedText, LOG_EOL));
      return null;
    }
    else {
      if (consoleBuffer.isEmpty()) {
        if (StringUtil.startsWith(trimmedText, LOG_START) && StringUtil.endsWith(trimmedText, LOG_END)) {
          eventMessage = text;
        }
        else {
          return new XmlEventOrOutput.Output(text);
        }
      }
      else {
        consoleBuffer.append(text);
        if (trimmedText.isEmpty()) return null;
      }
    }

    if (eventMessage == null) {
      String bufferText = consoleBuffer.toString();
      String trimmedBufferText = bufferText.trim();
      consoleBuffer.setLength(0);
      if (!StringUtil.startsWith(trimmedBufferText, LOG_START) || !StringUtil.endsWith(trimmedBufferText, LOG_END)) {
        return new XmlEventOrOutput.Output(bufferText);
      }
      eventMessage = trimmedBufferText;
    }
    assert consoleBuffer.isEmpty();
    executionConsole.setLastOutputXmlEvent(true);
    return new XmlEventOrOutput.XmlEvent(eventMessage);
  }

  private sealed interface XmlEventOrOutput permits XmlEventOrOutput.XmlEvent, XmlEventOrOutput.Output {
    record XmlEvent(@NotNull @NonNls String xml) implements XmlEventOrOutput {}
    record Output(@NotNull @NlsSafe String text) implements XmlEventOrOutput {}
  }
}
