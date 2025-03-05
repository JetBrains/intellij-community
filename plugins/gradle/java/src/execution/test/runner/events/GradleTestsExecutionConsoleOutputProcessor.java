// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.execution.test.runner.events;

import com.intellij.execution.ui.ConsoleViewContentType;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.gradle.execution.test.runner.GradleTestsExecutionConsole;

/**
 * Created by eugene.petrenko@gmail.com
 */
public final class GradleTestsExecutionConsoleOutputProcessor {
  private static final Logger LOG = Logger.getInstance(GradleTestsExecutionConsoleOutputProcessor.class);
  private static final String LOG_EOL = "<ijLogEol/>";
  private static final String LOG_START = "<ijLog>";
  private static final String LOG_END = "</ijLog>";

  public static void onOutput(@NotNull GradleTestsExecutionConsole executionConsole,
                              @NotNull String text,
                              @NotNull Key<?> processOutputType) {
    var eventMessage = getEventMessage(executionConsole, text, processOutputType);
    if (eventMessage == null) return;

    try {
      var xml = new TestEventXPPXmlView(eventMessage);
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

  private static @Nullable String getEventMessage(@NotNull GradleTestsExecutionConsole executionConsole,
                                                  @NotNull String text,
                                                  @NotNull Key<?> processOutputType) {
    String eventMessage = null;
    final StringBuilder consoleBuffer = executionConsole.getBuffer();
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
          executionConsole.print(text, ConsoleViewContentType.getConsoleViewType(processOutputType));
          return null;
        }
      }
      else {
        consoleBuffer.append(text);
        if (trimmedText.isEmpty()) return null;
      }
    }

    if (eventMessage == null) {
      String bufferText = consoleBuffer.toString().trim();
      consoleBuffer.setLength(0);
      if (!StringUtil.startsWith(bufferText, LOG_START) || !StringUtil.endsWith(bufferText, LOG_END)) {
        executionConsole.print(bufferText, ConsoleViewContentType.getConsoleViewType(processOutputType));
        return null;
      }
      eventMessage = bufferText;
    }
    assert consoleBuffer.isEmpty();
    return eventMessage;
  }
}
