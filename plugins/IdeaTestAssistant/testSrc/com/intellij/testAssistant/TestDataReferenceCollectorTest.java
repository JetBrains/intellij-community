/*
 * Copyright 2000-2010 JetBrains s.r.o.
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
package com.intellij.testAssistant;

import com.intellij.openapi.application.PluginPathManager;
import com.intellij.psi.PsiJavaFile;
import com.intellij.psi.PsiMethod;
import com.intellij.testFramework.TestDataPath;
import com.intellij.testFramework.fixtures.LightCodeInsightFixtureTestCase;

import java.util.List;

/**
 * @author yole
 */
@TestDataPath("$CONTENT_ROOT/testData/")
public class TestDataReferenceCollectorTest extends LightCodeInsightFixtureTestCase {
  public void testFixtureConfigureByFile() throws Exception {
    final List<String> references = doTest();
    assertEquals(1, references.size());
    assertEquals("fixtureConfigureByFile", references.get(0));
  }

  public void testDoTestParameters() throws Exception {
    final List<String> references = doTest();
    assertEquals(1, references.size());
    assertEquals("doTestParameters.java", references.get(0));
  }

  public void testDoFileTest() throws Exception {
    final List<String> references = doTest();
    assertEquals(2, references.size());
    assertTrue(references.contains("before"));
    assertTrue(references.contains("after"));
  }

  public void testReferencesInAnyMethod() throws Exception {
    final List<String> references = doTest();
    assertEquals(1, references.size());
    assertEquals("before", references.get(0));
  }

  public void testTestNameAsParameter() throws Exception {
    final List<String> references = doTest();
    assertEquals(1, references.size());
    assertEquals("beforeTestNameAsParameter", references.get(0));
  }

  private List<String> doTest() throws Exception {
    myFixture.configureByFile("referenceCollector/" + getTestName(false) + ".java");
    final PsiMethod theMethod = ((PsiJavaFile)myFixture.getFile()).getClasses()[0].getMethods()[0];
    return new TestDataReferenceCollector("", theMethod.getName().substring(4)).collectTestDataReferences(theMethod);
  }

  @Override
  protected String getBasePath() {
    return PluginPathManager.getPluginHomePathRelative("IdeaTestAssistant") + "/testData/";
  }
}
