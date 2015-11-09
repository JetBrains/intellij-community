/*
 * Copyright 2000-2014 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.plugins.gradle.execution.test.runner.events;

import com.intellij.execution.testframework.sm.runner.SMTestProxy;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.Couple;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.ObjectUtils;
import org.jetbrains.plugins.gradle.execution.test.runner.GradleTestsExecutionConsole;
import org.jetbrains.plugins.gradle.util.XmlXpathHelper;

import java.util.ArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @author Vladislav.Soroka
 * @since 2/28/14
 */
public class AfterTestEvent extends AbstractTestEvent {
  public AfterTestEvent(GradleTestsExecutionConsole executionConsole) {
    super(executionConsole);
  }

  @Override
  public void process(XmlXpathHelper eventXml) throws XmlXpathHelper.XmlParserException {

    final String testId = getTestId(eventXml);

    final String startTime = eventXml.queryXml("/ijLog/event/test/result/@startTime");
    final String endTime = eventXml.queryXml("/ijLog/event/test/result/@endTime");
    final String exceptionMsg = eventXml.queryXml("/ijLog/event/test/result/errorMsg");
    final String stackTrace = eventXml.queryXml("/ijLog/event/test/result/stackTrace");

    final SMTestProxy testProxy = findTestProxy(testId);
    if (testProxy == null) return;

    try {
      testProxy.setDuration(Long.valueOf(endTime) - Long.valueOf(startTime));
    }
    catch (NumberFormatException ignored) {
    }

    final CompositeRunnable runInEdt = new CompositeRunnable();
    final TestEventResult result = getTestEventResultType(eventXml);
    switch (result) {
      case SUCCESS:
        runInEdt.add(new Runnable() {
          @Override
          public void run() {
            testProxy.setFinished();
          }
        });
        break;
      case FAILURE:
        final String failureType = eventXml.queryXml("/ijLog/event/test/result/failureType");
        if ("comparison".equals(failureType)) {
          String actualText = eventXml.queryXml("/ijLog/event/test/result/actual");
          String expectedText = eventXml.queryXml("/ijLog/event/test/result/expected");
          final Condition<String> emptyString = new Condition<String>() {
            @Override
            public boolean value(String s) {
              return StringUtil.isEmpty(s);
            }
          };
          String filePath = ObjectUtils.nullizeByCondition(
            eventXml.queryXml("/ijLog/event/test/result/filePath"), emptyString);
          String actualFilePath = ObjectUtils.nullizeByCondition(
            eventXml.queryXml("/ijLog/event/test/result/actualFilePath"), emptyString);
          testProxy.setTestComparisonFailed(exceptionMsg, stackTrace, actualText, expectedText, filePath, actualFilePath);
        }
        else {
          Couple<String> comparisonPair =
            parseComparisonMessage(exceptionMsg, "\nExpected: is \"(.*)\"\n\\s*got: \"(.*)\"\n");
          if (comparisonPair == null) {
            comparisonPair = parseComparisonMessage(exceptionMsg, "\nExpected: is \"(.*)\"\n\\s*but: was \"(.*)\"");
          }
          if (comparisonPair == null) {
            comparisonPair = parseComparisonMessage(exceptionMsg, "\nExpected: (.*)\n\\s*got: (.*)");
          }
          if (comparisonPair == null) {
            comparisonPair = parseComparisonMessage(exceptionMsg, "\\s*expected same:<(.*)> was not:<(.*)>");
          }
          if (comparisonPair == null) {
            comparisonPair = parseComparisonMessage(exceptionMsg, ".*\\s*expected:<(.*)> but was:<(.*)>");
          }
          if (comparisonPair == null) {
            comparisonPair = parseComparisonMessage(exceptionMsg, "\nExpected: \"(.*)\"\n\\s*but: was \"(.*)\"");
          }

          final Couple<String> finalComparisonPair = comparisonPair;
          runInEdt.add(new Runnable() {
            @Override
            public void run() {
              if (finalComparisonPair != null) {
                testProxy.setTestComparisonFailed(exceptionMsg, stackTrace, finalComparisonPair.second, finalComparisonPair.first);
              }
              else {
                testProxy.setTestFailed(exceptionMsg, stackTrace, "error".equals(failureType));
              }
            }
          });
        }
        runInEdt.add(new Runnable() {
          @Override
          public void run() {
            getResultsViewer().onTestFailed(testProxy);
          }
        });
        break;
      case SKIPPED:
        runInEdt.add(new Runnable() {
          @Override
          public void run() {
            testProxy.setTestIgnored(null, null);
            getResultsViewer().onTestIgnored(testProxy);
          }
        });
        break;
      case UNKNOWN_RESULT:
        break;
    }

    runInEdt.add(new Runnable() {
      @Override
      public void run() {
        getResultsViewer().onTestFinished(testProxy);
      }
    });

    addToInvokeLater(runInEdt);
  }

  private static Couple<String> parseComparisonMessage(String message, final String regex) {
    final Matcher matcher = Pattern.compile(regex, Pattern.DOTALL | Pattern.CASE_INSENSITIVE).matcher(message);
    if (matcher.matches()) {
      return Couple.of(matcher.group(1).replaceAll("\\\\n", "\n"), matcher.group(2).replaceAll("\\\\n", "\n"));
    }
    return null;
  }

  private static class CompositeRunnable extends ArrayList<Runnable> implements Runnable {
    @Override
    public void run() {
      for (Runnable runnable : this) {
        runnable.run();
      }
    }
  }
}
