/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package org.jetbrains.plugins.groovy.lang;

import com.intellij.codeInsight.daemon.LineMarkerInfo;
import com.intellij.codeInsight.daemon.impl.DaemonCodeAnalyzerImpl;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.testFramework.fixtures.LightCodeInsightFixtureTestCase;
import com.intellij.testFramework.fixtures.impl.JavaCodeInsightTestFixtureImpl;
import org.jetbrains.plugins.groovy.util.TestUtils;

import java.util.List;

/**
 * @author Maxim.Medvedev
 */
public class GroovyLineMarkerTest extends LightCodeInsightFixtureTestCase {
  @Override
  protected String getBasePath() {
    return TestUtils.getTestDataPath() + "lineMarker/";
  }

  public void testInterface() throws Throwable {
    doSimpleTest(3);}
  public void testGStringMethodName() throws Throwable {
    doSimpleTest(3);}
  public void testStringMethodName() throws Throwable {
    doSimpleTest(3);}
  public void testAllGStringMethodName() throws Throwable {
    doSimpleTest(3);}

  public void testJavaToGroovy() throws Throwable {
    myFixture.configureByFiles(getTestName(false)+".groovy", getTestName(false)+".java");
    doTest(1);
  }

  public void testGroovyToJava() throws Throwable {
    myFixture.configureByFiles(getTestName(false)+".groovy", getTestName(false)+".java");
    doTest(2);
  }

  public void testJavaToGroovy2() throws Throwable {
    myFixture.configureByFiles("JavaToGroovy.java", "JavaToGroovy.groovy");
    doTest(2);
  }

  public void testGroovyToJava2() throws Throwable {
    myFixture.configureByFiles("GroovyToJava.java", "GroovyToJava.groovy");
    doTest(1);
  }

  private void doSimpleTest(int count) throws Throwable{
    myFixture.configureByFile(getTestName(false)+".groovy");
    doTest(count);
  }

  private void doTest(int count) {
    final Editor editor = myFixture.getEditor();
    final Project project = myFixture.getProject();

    myFixture.doHighlighting();

    final List<LineMarkerInfo> infoList = DaemonCodeAnalyzerImpl.getLineMarkers(editor.getDocument(), project);
    assertNotNull(infoList);
    assertEquals(count, infoList.size());
  }
}
