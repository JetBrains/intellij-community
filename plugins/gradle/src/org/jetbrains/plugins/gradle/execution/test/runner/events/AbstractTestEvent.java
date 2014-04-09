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

import com.intellij.execution.testframework.sm.runner.ui.SMTestRunnerResultsForm;
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.gradle.util.XmlXpathHelper;
import org.jetbrains.plugins.gradle.execution.test.runner.GradleConsoleProperties;
import org.jetbrains.plugins.gradle.execution.test.runner.GradleTestsExecutionConsoleManager;

/**
 * @author Vladislav.Soroka
 * @since 2/28/14
 */
public abstract class AbstractTestEvent implements TestEvent {
  private final GradleTestsExecutionConsoleManager myConsoleManager;

  public AbstractTestEvent(GradleTestsExecutionConsoleManager consoleManager) {
    this.myConsoleManager = consoleManager;
  }

  public GradleTestsExecutionConsoleManager getConsoleManager() {
    return myConsoleManager;
  }

  protected SMTestRunnerResultsForm getResultsViewer() {
    return myConsoleManager.getExecutionConsole().getResultsViewer();
  }

  protected Project getProject() {
    return myConsoleManager.getExecutionConsole().getProperties().getProject();
  }

  protected GradleConsoleProperties getProperties() {
    return (GradleConsoleProperties)getConsoleManager().getExecutionConsole().getProperties();
  }

  @Nullable
  protected String findLocationUrl(@Nullable String name, @NotNull String fqClassName) {
    return name == null ? "gradle://className::" + fqClassName : "gradle://methodName::" + fqClassName + '.' + name;
  }

  protected String getTestName(@NotNull XmlXpathHelper eventXml) throws XmlXpathHelper.XmlParserException {
    return eventXml.queryXml("/ijLog/event/test/descriptor/@name");
  }

  protected String getParentTestId(@NotNull XmlXpathHelper eventXml) throws XmlXpathHelper.XmlParserException {
    return eventXml.queryXml("/ijLog/event/test/@parentId");
  }

  protected String getTestId(@NotNull XmlXpathHelper eventXml) throws XmlXpathHelper.XmlParserException {
    return eventXml.queryXml("/ijLog/event/test/@id");
  }

  protected String getTestClassName(@NotNull XmlXpathHelper eventXml) throws XmlXpathHelper.XmlParserException {
    return eventXml.queryXml("/ijLog/event/test/descriptor/@className");
  }

  protected TestEventResult getTestEventResultType(@NotNull XmlXpathHelper eventXml) throws XmlXpathHelper.XmlParserException {
    return TestEventResult.fromValue(eventXml.queryXml("/ijLog/event/test/result/@resultType"));
  }

  protected void addToInvokeLater(final Runnable runnable) {
    ExternalSystemApiUtil.addToInvokeLater(runnable);
  }
}
