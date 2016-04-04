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
import org.jetbrains.plugins.gradle.execution.test.runner.GradleTestsExecutionConsole;
import org.jetbrains.plugins.gradle.util.XmlXpathHelper;

/**
 * @author Vladislav.Soroka
 * @since 2/28/14
 */
public class AfterSuiteEvent extends AbstractTestEvent {
  public AfterSuiteEvent(GradleTestsExecutionConsole executionConsole) {
    super(executionConsole);
  }

  @Override
  public void process(XmlXpathHelper eventXml) throws XmlXpathHelper.XmlParserException {
    final String testId = getTestId(eventXml);
    final TestEventResult result = getTestEventResultType(eventXml);

    addToInvokeLater(new Runnable() {
      @Override
      public void run() {
        final SMTestProxy testProxy = findTestProxy(testId);
        if (testProxy == null) return;

        switch (result) {
          case SUCCESS:
            testProxy.setFinished();
            break;
          case FAILURE:
            testProxy.setTestFailed("", null, false);
            break;
          case SKIPPED:
            testProxy.setTestIgnored(null, null);
            break;
          case UNKNOWN_RESULT:
            break;
        }

        getResultsViewer().onSuiteFinished(testProxy);
      }
    });
  }
}
