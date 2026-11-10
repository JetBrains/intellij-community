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

package org.jetbrains.plugins.groovy.lang;

import com.intellij.codeInsight.daemon.LineMarkerInfo;
import com.intellij.codeInsight.daemon.impl.LineMarkersPass;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.util.TestUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaCodeInsightFixture;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * @author Maxim.Medvedev
 */
@TestApplication
public class GroovyLineMarkerTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture();
  private final TestFixture<LightJavaCodeInsightTestFixture> codeInsightFixture = lightJavaCodeInsightFixture(projectFixture);

  @BeforeEach
  void setUpTestDataPath() {
    codeInsightFixture.get().setTestDataPath(TestUtils.getAbsoluteTestDataPath() + "lineMarker/");
  }

  @Test
  public void testInterface() {
    doSimpleTest(3);}
  @Test
  public void testGStringMethodName() {
    doSimpleTest(3);}
  @Test
  public void testStringMethodName() {
    doSimpleTest(3);}
  @Test
  public void testAllGStringMethodName() {
    doSimpleTest(3);}

  @Test
  public void testJavaToGroovy() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.configureByFiles(myFixture.getTestName(false)+".groovy", myFixture.getTestName(false)+".java");
    doTest(1);
  }

  @Test
  public void testGroovyToJava() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.configureByFiles(myFixture.getTestName(false)+".groovy", myFixture.getTestName(false)+".java");
    doTest(2);
  }

  @Test
  public void testJavaToGroovy2() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.configureByFiles("JavaToGroovy.java", "JavaToGroovy.groovy");
    doTest(2);
  }

  @Test
  public void testGroovyToJava2() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.configureByFiles("GroovyToJava.java", "GroovyToJava.groovy");
    doTest(1);
  }

  private void doSimpleTest(int count) {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.configureByFile(myFixture.getTestName(false)+".groovy");
    doTest(count);
  }

  private void doTest(int count) {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    final Editor editor = myFixture.getEditor();
    final Project project = myFixture.getProject();

    myFixture.doHighlighting();

    @NotNull Document document = editor.getDocument();
    final List<LineMarkerInfo<?>> infoList = LineMarkersPass.getDisplayedLineMarkers(document, project);
    assertEquals(count, infoList.size());
  }
}
