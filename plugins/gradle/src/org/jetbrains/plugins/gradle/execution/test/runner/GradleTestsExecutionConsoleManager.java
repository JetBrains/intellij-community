/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package org.jetbrains.plugins.gradle.execution.test.runner;

import com.intellij.execution.ExecutionException;
import com.intellij.execution.Executor;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.execution.testframework.sm.SMTestRunnerConnectionUtil;
import com.intellij.execution.testframework.sm.runner.SMTestProxy;
import com.intellij.execution.testframework.sm.runner.states.TestStateInfo;
import com.intellij.execution.testframework.sm.runner.ui.SMRootTestProxyFormatter;
import com.intellij.execution.testframework.sm.runner.ui.SMTRunnerConsoleView;
import com.intellij.execution.testframework.sm.runner.ui.TestTreeRenderer;
import com.intellij.execution.ui.ConsoleViewContentType;
import com.intellij.execution.ui.ExecutionConsole;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.externalSystem.execution.ExternalSystemExecutionConsoleManager;
import com.intellij.openapi.externalSystem.model.ProjectSystemId;
import com.intellij.openapi.externalSystem.model.execution.ExternalTaskPojo;
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTask;
import com.intellij.openapi.externalSystem.service.execution.ExternalSystemRunConfiguration;
import com.intellij.openapi.externalSystem.service.internal.ExternalSystemExecuteTaskTask;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.util.ObjectUtils;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.gradle.util.XmlXpathHelper;
import org.jetbrains.plugins.gradle.execution.test.runner.events.*;
import org.jetbrains.plugins.gradle.util.GradleBundle;
import org.jetbrains.plugins.gradle.util.GradleConstants;

import java.util.Map;

/**
 * @author Vladislav.Soroka
 * @since 2/18/14
 */
public class GradleTestsExecutionConsoleManager implements ExternalSystemExecutionConsoleManager<ExternalSystemRunConfiguration> {
  private static final Logger LOG = Logger.getInstance(GradleTestsExecutionConsoleManager.class);

  private Map<String, SMTestProxy> testsMap = ContainerUtil.newHashMap();
  private StringBuilder myBuffer = new StringBuilder();
  private SMTRunnerConsoleView myExecutionConsole;

  @NotNull
  @Override
  public ProjectSystemId getExternalSystemId() {
    return GradleConstants.SYSTEM_ID;
  }

  public Map<String, SMTestProxy> getTestsMap() {
    return testsMap;
  }

  public GradleUrlProvider getUrlProvider() {
    return GradleUrlProvider.INSTANCE;
  }

  public SMTRunnerConsoleView getExecutionConsole() {
    return myExecutionConsole;
  }

  @NotNull
  @Override
  public ExecutionConsole attachExecutionConsole(@NotNull final ExternalSystemTask task,
                                                 @NotNull final Project project,
                                                 @NotNull final ExternalSystemRunConfiguration configuration,
                                                 @NotNull final Executor executor,
                                                 @NotNull final ExecutionEnvironment env,
                                                 @NotNull final ProcessHandler processHandler) throws ExecutionException {
    final GradleConsoleProperties properties = new GradleConsoleProperties(configuration, executor);
    myExecutionConsole = (SMTRunnerConsoleView)SMTestRunnerConnectionUtil.createAndAttachConsole(
      configuration.getSettings().getExternalSystemId().getReadableName(), processHandler, properties);

    TestTreeRenderer originalRenderer =
      ObjectUtils.tryCast(myExecutionConsole.getResultsViewer().getTreeView().getCellRenderer(), TestTreeRenderer.class);
    if (originalRenderer != null) {
      originalRenderer.setAdditionalRootFormatter(new SMRootTestProxyFormatter() {
        @Override
        public void format(@NotNull SMTestProxy.SMRootTestProxy testProxy, @NotNull TestTreeRenderer renderer) {
          final TestStateInfo.Magnitude magnitude = testProxy.getMagnitudeInfo();
          if (magnitude == TestStateInfo.Magnitude.RUNNING_INDEX) {
            renderer.clear();
            renderer.append(GradleBundle.message(
                              "gradle.test.runner.ui.tests.tree.presentation.labels.waiting.tests"),
                            SimpleTextAttributes.REGULAR_ATTRIBUTES
            );
          }
          else if (!testProxy.isInProgress() && testProxy.isEmptySuite()) {
            renderer.clear();
            renderer.append(GradleBundle.message(
                              "gradle.test.runner.ui.tests.tree.presentation.labels.no.tests.were.found"),
                            SimpleTextAttributes.REGULAR_ATTRIBUTES
            );
          }
        }
      });
    }
    return myExecutionConsole;
  }

  @Override
  public void onOutput(@NotNull String text, @NotNull Key processOutputType) {
    if (StringUtil.endsWith(text, "<ijLogEol/>\n")) {
      myBuffer.append(StringUtil.trimEnd(text, "<ijLogEol/>\n")).append('\n');
      return;
    }
    else {
      myBuffer.append(text);
    }

    String trimmedText = myBuffer.toString().trim();
    myBuffer.setLength(0);

    if (!StringUtil.startsWith(trimmedText, "<ijLog>") || !StringUtil.endsWith(trimmedText, "</ijLog>")) {
      if (text.trim().isEmpty()) return;
      myExecutionConsole.print(text, ConsoleViewContentType.getConsoleViewType(processOutputType));
      return;
    }

    try {
      final XmlXpathHelper xml = new XmlXpathHelper(trimmedText);

      final TestEventType eventType = TestEventType.fromValue(xml.queryXml("/ijLog/event/@type"));
      TestEvent testEvent = null;
      switch (eventType) {
        case CONFIGURATION_ERROR:
          testEvent = new ConfigurationErrorEvent(this);
          break;
        case REPORT_LOCATION:
          testEvent = new ReportLocationEvent(this);
          break;
        case BEFORE_TEST:
          testEvent = new BeforeTestEvent(this);
          break;
        case ON_OUTPUT:
          testEvent = new OnOutputEvent(this);
          break;
        case AFTER_TEST:
          testEvent = new AfterTestEvent(this);
          break;
        case BEFORE_SUITE:
          testEvent = new BeforeSuiteEvent(this);
          break;
        case AFTER_SUITE:
          testEvent = new AfterSuiteEvent(this);
          break;
        case UNKNOWN_EVENT:
          break;
      }
      if (testEvent != null) {
        testEvent.process(xml);
      }
    }
    catch (XmlXpathHelper.XmlParserException e) {
      LOG.error("Gradle test events parser error", e);
    }
  }

  @Override
  public boolean isApplicableFor(@NotNull ExternalSystemTask task) {
    if (task instanceof ExternalSystemExecuteTaskTask) {
      ExternalSystemExecuteTaskTask taskTask = (ExternalSystemExecuteTaskTask)task;
      if (!StringUtil.equals(taskTask.getExternalSystemId().getId(), GradleConstants.SYSTEM_ID.getId())) return false;

      return ContainerUtil.find(taskTask.getTasksToExecute(), new Condition<ExternalTaskPojo>() {
        @Override
        public boolean value(ExternalTaskPojo pojo) {
          return "test".equals(pojo.getName());
        }
      }) != null;
    }
    return false;
  }

  @Override
  public AnAction[] getRestartActions() {
    return new AnAction[0];
  }
}
