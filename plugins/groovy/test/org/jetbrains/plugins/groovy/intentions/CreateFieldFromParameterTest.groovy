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
import com.intellij.codeInsight.intention.impl.config.IntentionActionWrapper
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.testFramework.fixtures.LightCodeInsightFixtureTestCase
import org.jetbrains.plugins.groovy.intentions.declaration.GrCreateFieldForParameterIntention
import org.jetbrains.plugins.groovy.util.TestUtils
/**
 * @author Max Medvedev
 */
class CreateFieldFromParameterTest extends LightCodeInsightFixtureTestCase {
  @Override
  protected String getBasePath() {
    "${TestUtils.testDataPath}intentions/createFieldFromParameter/"
  }

  void test1() {doTest()}
  void test2() {doTest()}
  void test3() {doTest()}
  void test4() {doTest()}
  void _test5() {doTest()}
  void _test6() {doTest()}
  void _test7() {doTest()}
  void test8() {doTest()}
  void testArrayType() {doTest()}
  void testBoundListTypeParameter() {doTest()}
  void _testCaretOnMethod() {doTest()}
  void _testCaretOnMethodWithOnlyAssignedParams() {doTest()}
  void _testCaretOnMethodWithoutParams() {doTest()}
  void testClassTypeParameter() {doTest()}
  void testListClassTypeParameter() {doTest()}
  void testListTypeParameter() {doTest()}
  void _testNotNull() {doTest()}
  void _testNullable() {doTest()}
  void testSimpleTypeParameter() {doTest()}
  void testTypeParameter() {doTest()}

  private void doTest() {
    myFixture.configureByFile("before${getTestName(false)}.groovy")
    def intentions = myFixture.availableIntentions
    for (intention in intentions) {
      if (intention instanceof IntentionActionWrapper) intention = intention.delegate
      if (intention instanceof GrCreateFieldForParameterIntention) {
        WriteCommandAction.runWriteCommandAction(null, new Runnable() {
          void run() {
            intention.invoke(myFixture.project, myFixture.editor, myFixture.file)
            doPostponedFormatting(myFixture.project)
          }
        })
        break
      }
    }
    myFixture.checkResultByFile("after${getTestName(false)}.groovy")
  }
}
