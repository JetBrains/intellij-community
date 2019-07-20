// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.refactoring.introduceParameter

import com.intellij.psi.impl.source.PostprocessReformattingAspect
import com.intellij.refactoring.IntroduceParameterRefactoring
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase
import groovy.transform.CompileStatic
import org.jetbrains.annotations.Nullable
import org.jetbrains.plugins.groovy.util.TestUtils

/**
 * @author Max Medvedev
 */
@CompileStatic
class GrIntroduceParameterInClosureTest extends LightJavaCodeInsightFixtureTestCase {
  protected String getBasePath() {
    return TestUtils.getTestDataPath() + "refactoring/introduceParameterInClosure/"
  }

  private void doTest(final int replaceFieldsWithGetters,
                      final boolean removeUnusedParameters,
                      final boolean declareFinal,
                      @Nullable final String conflicts,
                      final boolean generateDelegate) {
    myFixture.configureByFile(getTestName(false) + ".groovy")

    GrIntroduceParameterTest.execute(replaceFieldsWithGetters, removeUnusedParameters, declareFinal, conflicts, generateDelegate,
                                     getProject(), myFixture.getEditor(), myFixture.getFile())

    PostprocessReformattingAspect.getInstance(getProject()).doPostponedFormatting()
    myFixture.getEditor().getSelectionModel().removeSelection()

    myFixture.checkResultByFile(getTestName(false) + "_after.groovy")
  }

  private void doTest(final int replaceFieldsWithGetters,
                          final boolean removeUnusedParameters,
                          final boolean declareFinal,
                          @Nullable final String conflicts,
                          final boolean generateDelegate,
                          String before,
                          String after) {
    myFixture.configureByText('before.groovy', before)

    GrIntroduceParameterTest.execute(replaceFieldsWithGetters, removeUnusedParameters, declareFinal, conflicts, generateDelegate,
                                     getProject(), myFixture.getEditor(), myFixture.getFile())

    PostprocessReformattingAspect.getInstance(getProject()).doPostponedFormatting()
    myFixture.getEditor().getSelectionModel().removeSelection()

    myFixture.checkResult(after)
  }

  void testSimpleClosure() {
    doTest(IntroduceParameterRefactoring.REPLACE_FIELDS_WITH_GETTERS_NONE, false, false, null, false)
  }

  void testRemoveUnusedParam() {
    doTest(IntroduceParameterRefactoring.REPLACE_FIELDS_WITH_GETTERS_NONE, true, false, null, false)
  }

  void testLocalVarUsage() {
    doTest(IntroduceParameterRefactoring.REPLACE_FIELDS_WITH_GETTERS_NONE, false, false, null, false)
  }

  void testField() {
    doTest(IntroduceParameterRefactoring.REPLACE_FIELDS_WITH_GETTERS_ALL, true, true, null, false)
  }

  void testReplaceWithGetter() {
    doTest(IntroduceParameterRefactoring.REPLACE_FIELDS_WITH_GETTERS_ALL, true, true, null, false)
  }

  void testGetter() {
    doTest(IntroduceParameterRefactoring.REPLACE_FIELDS_WITH_GETTERS_NONE, true, true, null, false)
  }

  void testReplaceGetterCall() {
    doTest(IntroduceParameterRefactoring.REPLACE_FIELDS_WITH_GETTERS_ALL, true, true, null, false)
  }

  void testClosureRefWithoutCall() {
    doTest(IntroduceParameterRefactoring.REPLACE_FIELDS_WITH_GETTERS_NONE, false, false, null, false)
  }

  void testClosureCall() {
    doTest(IntroduceParameterRefactoring.REPLACE_FIELDS_WITH_GETTERS_NONE, false, false, null, false)
  }

  void testVarAssignedToClosure() {
    doTest(IntroduceParameterRefactoring.REPLACE_FIELDS_WITH_GETTERS_NONE, false, false, null, false)
  }

  void testCorrectOccurrencesForLocalVar() {
    doTest(IntroduceParameterRefactoring.REPLACE_FIELDS_WITH_GETTERS_NONE, false, false, null, false)
  }

  /*public void testDelegate() {
    doTest(IntroduceParameterRefactoring.REPLACE_FIELDS_WITH_GETTERS_NONE, false, false, null, true);
  }

  public void testDelegateRemoveUnusedParam() {
    doTest(IntroduceParameterRefactoring.REPLACE_FIELDS_WITH_GETTERS_NONE, true, false, null, true);
  }*/

  void testStringPart0() {
    doTest(IntroduceParameterRefactoring.REPLACE_FIELDS_WITH_GETTERS_NONE, false, false, null, false, '''\
def cl = {
    print 'a<selection>b</selection>c'
}
cl()
''', '''\
def cl = { String anObject ->
    print 'a' + anObject<caret> + 'c'
}
cl('b')
''')
  }
}
