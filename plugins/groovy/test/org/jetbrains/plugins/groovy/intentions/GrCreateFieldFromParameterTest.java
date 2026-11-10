/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package org.jetbrains.plugins.groovy.intentions;

import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.util.TestUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaCodeInsightFixture;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;

/**
 * @author Max Medvedev
 */
@TestApplication
public class GrCreateFieldFromParameterTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture();
  private final TestFixture<LightJavaCodeInsightTestFixture> codeInsightFixture = lightJavaCodeInsightFixture(projectFixture);

  @BeforeEach
  void setUpTestDataPath() {
    codeInsightFixture.get().setTestDataPath(TestUtils.getAbsoluteTestDataPath() + "intentions/createFieldFromParameter/");
  }

  @Test
  public void test1() { doTest("Create field for parameter 'id'"); }

  @Test
  public void test2() { doTest("Create field for parameter 'test'"); }

  @Test
  public void test3() { doTest("Create field for parameter 'length'"); }

  @Test
  public void test4() { doTest("Create field for parameter 'p1'"); }

  public void _test5() { doTest(""); }

  public void _test6() { doTest(""); }

  public void _test7() { doTest(""); }

  @Test
  public void test8() { doTest("Create field for parameter 'p1'"); }

  @Test
  public void testArrayType() { doTest("Create field for parameter 'p1'"); }

  @Test
  public void testBoundListTypeParameter() { doTest("Create field for parameter 'p1'"); }

  public void _testCaretOnMethod() { doTest(""); }

  public void _testCaretOnMethodWithOnlyAssignedParams() { doTest(""); }

  public void _testCaretOnMethodWithoutParams() { doTest(""); }

  @Test
  public void testClassTypeParameter() { doTest("Create field for parameter 'p1'"); }

  @Test
  public void testListClassTypeParameter() { doTest("Create field for parameter 'p1'"); }

  @Test
  public void testListTypeParameter() { doTest("Create field for parameter 'p1'"); }

  public void _testNotNull() { doTest(""); }

  public void _testNullable() { doTest(""); }

  @Test
  public void testSimpleTypeParameter() { doTest("Create field for parameter 'p1'"); }

  @Test
  public void testTypeParameter() { doTest("Create field for parameter 'p1'"); }

  private void doTest(@NotNull String hint) {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.configureByFile("before" + myFixture.getTestName(false) + ".groovy");
    IntentionAction intention = myFixture.findSingleIntention(hint);
    myFixture.launchAction(intention);
    myFixture.checkResultByFile("after" + myFixture.getTestName(false) + ".groovy");
  }
}
