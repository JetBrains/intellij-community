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
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.util.TestUtils;

/**
 * @author Max Medvedev
 */
public class GrCreateFieldFromParameterTest extends LightJavaCodeInsightFixtureTestCase {
  @Override
  protected String getBasePath() {
    return TestUtils.getTestDataPath() + "intentions/createFieldFromParameter/";
  }

  public void test1() { doTest("Create field for parameter 'id'"); }

  public void test2() { doTest("Create field for parameter 'test'"); }

  public void test3() { doTest("Create field for parameter 'length'"); }

  public void test4() { doTest("Create field for parameter 'p1'"); }

  public void _test5() { doTest(""); }

  public void _test6() { doTest(""); }

  public void _test7() { doTest(""); }

  public void test8() { doTest("Create field for parameter 'p1'"); }

  public void testArrayType() { doTest("Create field for parameter 'p1'"); }

  public void testBoundListTypeParameter() { doTest("Create field for parameter 'p1'"); }

  public void _testCaretOnMethod() { doTest(""); }

  public void _testCaretOnMethodWithOnlyAssignedParams() { doTest(""); }

  public void _testCaretOnMethodWithoutParams() { doTest(""); }

  public void testClassTypeParameter() { doTest("Create field for parameter 'p1'"); }

  public void testListClassTypeParameter() { doTest("Create field for parameter 'p1'"); }

  public void testListTypeParameter() { doTest("Create field for parameter 'p1'"); }

  public void _testNotNull() { doTest(""); }

  public void _testNullable() { doTest(""); }

  public void testSimpleTypeParameter() { doTest("Create field for parameter 'p1'"); }

  public void testTypeParameter() { doTest("Create field for parameter 'p1'"); }

  private void doTest(@NotNull String hint) {
    myFixture.configureByFile("before" + getTestName(false) + ".groovy");
    IntentionAction intention = myFixture.findSingleIntention(hint);
    myFixture.launchAction(intention);
    myFixture.checkResultByFile("after" + getTestName(false) + ".groovy");
  }
}
