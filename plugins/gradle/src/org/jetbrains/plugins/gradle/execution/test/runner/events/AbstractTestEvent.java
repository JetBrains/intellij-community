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
import com.intellij.execution.testframework.sm.runner.ui.SMTestRunnerResultsForm;
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.gradle.execution.GradleRunnerUtil;
import org.jetbrains.plugins.gradle.execution.test.runner.GradleConsoleProperties;
import org.jetbrains.plugins.gradle.execution.test.runner.GradleTestsExecutionConsole;
import org.jetbrains.plugins.gradle.util.XmlXpathHelper;

/**
 * @author Vladislav.Soroka
 * @since 2/28/14
 */
public abstract class AbstractTestEvent implements TestEvent {
  private final GradleTestsExecutionConsole myExecutionConsole;

  public AbstractTestEvent(GradleTestsExecutionConsole executionConsole) {
    this.myExecutionConsole = executionConsole;
  }

  public GradleTestsExecutionConsole getExecutionConsole() {
    return myExecutionConsole;
  }

  protected SMTestRunnerResultsForm getResultsViewer() {
    return myExecutionConsole.getResultsViewer();
  }

  protected Project getProject() {
    return myExecutionConsole.getProperties().getProject();
  }

  protected GradleConsoleProperties getProperties() {
    return (GradleConsoleProperties)getExecutionConsole().getProperties();
  }

  @NotNull
  protected String findLocationUrl(@Nullable String name, @NotNull String fqClassName) {
    return GradleRunnerUtil.getTestLocationUrl(name, fqClassName);
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

  @Nullable
  protected SMTestProxy findTestProxy(final String proxyId) {
    return getExecutionConsole().getTestsMap().get(proxyId);
  }

  protected void registerTestProxy(final String proxyId, SMTestProxy testProxy) {
    myExecutionConsole.getTestsMap().put(proxyId, testProxy);
  }
}
