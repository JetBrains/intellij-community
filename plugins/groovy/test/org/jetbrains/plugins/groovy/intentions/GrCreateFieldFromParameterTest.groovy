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
package org.jetbrains.plugins.groovy.intentions

import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase
import org.jetbrains.annotations.NotNull
import org.jetbrains.plugins.groovy.util.TestUtils
/**
 * @author Max Medvedev
 */
class GrCreateFieldFromParameterTest extends LightJavaCodeInsightFixtureTestCase {
  @Override
  protected String getBasePath() {
    "${TestUtils.testDataPath}intentions/createFieldFromParameter/"
  }

  void test1() {doTest("Create field for parameter 'id'")}
  void test2() {doTest("Create field for parameter 'test'")}
  void test3() {doTest("Create field for parameter 'length'")}
  void test4() {doTest("Create field for parameter 'p1'")}
  void _test5() {doTest("")}
  void _test6() {doTest("")}
  void _test7() {doTest("")}
  void test8() {doTest("Create field for parameter 'p1'")}
  void testArrayType() {doTest("Create field for parameter 'p1'")}
  void testBoundListTypeParameter() {doTest("Create field for parameter 'p1'")}
  void _testCaretOnMethod() {doTest("")}
  void _testCaretOnMethodWithOnlyAssignedParams() {doTest("")}
  void _testCaretOnMethodWithoutParams() {doTest("")}
  void testClassTypeParameter() {doTest("Create field for parameter 'p1'")}
  void testListClassTypeParameter() {doTest("Create field for parameter 'p1'")}
  void testListTypeParameter() {doTest("Create field for parameter 'p1'")}
  void _testNotNull() {doTest("")}
  void _testNullable() {doTest("")}
  void testSimpleTypeParameter() {doTest("Create field for parameter 'p1'")}
  void testTypeParameter() {doTest("Create field for parameter 'p1'")}

  private void doTest(@NotNull String hint) {
    myFixture.configureByFile("before${getTestName(false)}.groovy")
    def intention = myFixture.findSingleIntention(hint)
    myFixture.launchAction(intention)
    myFixture.checkResultByFile("after${getTestName(false)}.groovy")
  }
}
