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

import org.jetbrains.plugins.gradle.util.XmlXpathHelper;
import org.jetbrains.plugins.gradle.execution.test.runner.GradleTestsExecutionConsoleManager;

import java.io.File;

/**
 * @author Vladislav.Soroka
 * @since 2/28/14
 */
public class ReportLocationEvent extends AbstractTestEvent {

  public ReportLocationEvent(GradleTestsExecutionConsoleManager consoleManager) {
    super(consoleManager);
  }

  @Override
  public void process(XmlXpathHelper eventXml) throws XmlXpathHelper.XmlParserException {
    final String testReport = eventXml.queryXml("/ijLog/event/@testReport");
    assert testReport != null;
    getProperties().setGradleTestReport(new File(testReport));
  }
}
