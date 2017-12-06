/*
 * Copyright 2000-2016 JetBrains s.r.o.
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

package org.jetbrains.plugins.groovy.refactoring.introduceParameter

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.*
import com.intellij.psi.impl.source.PostprocessReformattingAspect
import com.intellij.refactoring.HelpID
import com.intellij.refactoring.IntroduceParameterRefactoring
import com.intellij.refactoring.introduceField.ElementToWorkOn
import com.intellij.refactoring.introduceParameter.IntroduceParameterProcessor
import com.intellij.refactoring.introduceParameter.Util
import com.intellij.testFramework.fixtures.LightCodeInsightFixtureTestCase
import gnu.trove.TIntArrayList
import org.jetbrains.annotations.NonNls
import org.jetbrains.annotations.Nullable
import org.jetbrains.plugins.groovy.util.TestUtils
/**
 * @author Maxim.Medvedev
 */
class IntroduceParameterTest extends LightCodeInsightFixtureTestCase {
  final String getBasePath() {
    return TestUtils.testDataPath + 'refactoring/introduceParameter/' + getTestName(true) + '/'
  }

  private void doTest(int replaceFieldsWithGetters, boolean removeUnusedParameters, boolean searchForSuper, boolean declareFinal, @Nullable String conflicts = null) {
    final String beforeGroovy = getTestName(false)+"Before.groovy"
    final String afterGroovy = getTestName(false) + "After.groovy"
    final String javaClass = getTestName(false) + "MyClass.java"
    myFixture.configureByFiles(javaClass, beforeGroovy)
    executeRefactoring(true, replaceFieldsWithGetters, "anObject", searchForSuper, declareFinal, removeUnusedParameters, conflicts)
    PostprocessReformattingAspect.getInstance(project).doPostponedFormatting()
    myFixture.checkResultByFile(beforeGroovy, afterGroovy, true)
  }


  private boolean executeRefactoring(boolean replaceAllOccurrences,
                                     int replaceFieldsWithGetters,
                                     @NonNls String parameterName,
                                     boolean searchForSuper,
                                     boolean declareFinal,
                                     final boolean removeUnusedParameters,
                                     final String conflicts) {
    boolean generateDelegate = false
    Editor editor = myFixture.editor

    final PsiFile myFile = myFixture.file
    final ElementToWorkOn[] elementToWorkOn = new ElementToWorkOn[1]
    ElementToWorkOn.processElementToWorkOn(editor, myFile, "INtr param", HelpID.INTRODUCE_PARAMETER, project, new ElementToWorkOn.ElementsProcessor<com.intellij.refactoring.introduceField.ElementToWorkOn>() {

            @Override
            boolean accept(ElementToWorkOn el) {
              return true
            }
      
            @Override
            void pass(final ElementToWorkOn e) {
              if (e == null) return

              elementToWorkOn[0] = e
            }
          })

    final PsiExpression expr = elementToWorkOn[0].expression
    final PsiLocalVariable localVar = elementToWorkOn[0].localVariable

    PsiElement context = expr == null ? localVar : expr
    PsiMethod method = Util.getContainingMethod(context)
    if (method == null) return false

    final PsiMethod methodToSearchFor
    if (searchForSuper) {
      final PsiMethod[] superMethods = method.findDeepestSuperMethods()
      methodToSearchFor = superMethods.length > 0 ? superMethods[0] : method        //findDeepestSuperMethod();
    }
    else {
      methodToSearchFor = method
    }

    PsiExpression initializer = expr == null ? localVar.initializer : expr
    assert initializer != null
    TIntArrayList parametersToRemove = removeUnusedParameters ? Util.findParametersToRemove(method, initializer, null) : new TIntArrayList()
    final Project project = myFixture.project
    final IntroduceParameterProcessor processor =
      new IntroduceParameterProcessor(project, method, methodToSearchFor, initializer, expr, localVar, true, parameterName,
                                      replaceAllOccurrences, replaceFieldsWithGetters, declareFinal, generateDelegate, null,
                                      parametersToRemove)

    try {
      processor.run()
      if (conflicts != null) fail("Conflicts were expected")
    }
    catch (Exception e) {
      if (conflicts == null){
        e.printStackTrace()
        fail("Conflicts were not expected")
      }
      assertEquals(conflicts, e.message)
    }

    editor.selectionModel.removeSelection()
    return true
  }


  void testSimpleOverridedMethod() {
    doTest(IntroduceParameterRefactoring.REPLACE_FIELDS_WITH_GETTERS_NONE, false, false, false)
  }

  void testOverridedMethodWithRemoveUnusedParameters() {
    doTest(IntroduceParameterRefactoring.REPLACE_FIELDS_WITH_GETTERS_NONE, true, false, false)
  }

  void testSimpleUsage() {
    doTest(IntroduceParameterRefactoring.REPLACE_FIELDS_WITH_GETTERS_NONE, false, false, false)
  }

  void testMethodWithoutParams() {
    doTest(IntroduceParameterRefactoring.REPLACE_FIELDS_WITH_GETTERS_NONE, false, false, false)
  }

  void testParameterSubstitution() {
    doTest(IntroduceParameterRefactoring.REPLACE_FIELDS_WITH_GETTERS_NONE, false, false, false)
  }

  void testThisSubstitution() {
    doTest(IntroduceParameterRefactoring.REPLACE_FIELDS_WITH_GETTERS_NONE, false, false, false)
  }

  void testThisSubstitutionInQualifier() {
    doTest(IntroduceParameterRefactoring.REPLACE_FIELDS_WITH_GETTERS_NONE, false, false, false, "field <b><code>Test.i</code></b> is not accessible from method <b><code>XTest.n()</code></b>. Value for introduced parameter in that method call will be incorrect.")
  }

  void testFieldAccess() {
    doTest(IntroduceParameterRefactoring.REPLACE_FIELDS_WITH_GETTERS_NONE, false, false, false)
  }

  void testMethodAccess() {
    doTest(IntroduceParameterRefactoring.REPLACE_FIELDS_WITH_GETTERS_NONE, false, false, false)
  }

  void testStaticFieldAccess() {
    doTest(IntroduceParameterRefactoring.REPLACE_FIELDS_WITH_GETTERS_NONE, false, false, false)
  }

  void testFieldWithGetterReplacement() {
    doTest(IntroduceParameterRefactoring.REPLACE_FIELDS_WITH_GETTERS_ALL, false, false, false)
  }

  void testFieldWithInaccessibleGetterReplacement() {
    doTest(IntroduceParameterRefactoring.REPLACE_FIELDS_WITH_GETTERS_INACCESSIBLE, false, false, false)
  }

  void testWeirdQualifier() {
    doTest(IntroduceParameterRefactoring.REPLACE_FIELDS_WITH_GETTERS_INACCESSIBLE, false, false, false)
  }

  void testSuperInExpression() {
    doTest(IntroduceParameterRefactoring.REPLACE_FIELDS_WITH_GETTERS_INACCESSIBLE, false, false, false, "Parameter initializer contains <b><code>super</code></b>, but not all calls to method are in its class")
  }

  void testWeirdQualifierAndParameter() {
    doTest(IntroduceParameterRefactoring.REPLACE_FIELDS_WITH_GETTERS_INACCESSIBLE, false, false, false)
  }

  void testImplicitSuperCall() {
    doTest(IntroduceParameterRefactoring.REPLACE_FIELDS_WITH_GETTERS_INACCESSIBLE, false, false, false)
  }

  void testImplicitDefaultConstructor() {
    doTest(IntroduceParameterRefactoring.REPLACE_FIELDS_WITH_GETTERS_INACCESSIBLE, false, false, false)
  }

  void testInternalSideEffect() {
    doTest(IntroduceParameterRefactoring.REPLACE_FIELDS_WITH_GETTERS_INACCESSIBLE, false, false, false)
  }

/*  public void testAnonymousClass() {
    doTest(IntroduceParameterRefactoring.REPLACE_FIELDS_WITH_GETTERS_INACCESSIBLE, false, false, false);
  }*/

  void testSuperWithSideEffect() {
    doTest(IntroduceParameterRefactoring.REPLACE_FIELDS_WITH_GETTERS_INACCESSIBLE, false, false, false, "Parameter initializer contains <b><code>super</code></b>, but not all calls to method are in its class")
  }

  void testConflictingField() {
    doTest(IntroduceParameterRefactoring.REPLACE_FIELDS_WITH_GETTERS_INACCESSIBLE, false, true, false)
  }

  /*public void testParameterJavaDoc1() {
    doTest(IntroduceParameterRefactoring.REPLACE_FIELDS_WITH_GETTERS_INACCESSIBLE, false, false, true);
  }

  public void testParameterJavaDoc2() {
    doTest(IntroduceParameterRefactoring.REPLACE_FIELDS_WITH_GETTERS_INACCESSIBLE, false, false, true);
  }

  public void testParameterJavaDoc3() {
    doTest(IntroduceParameterRefactoring.REPLACE_FIELDS_WITH_GETTERS_INACCESSIBLE, false, false, true);
  }

  public void testParameterJavaDocBeforeVararg() {
    doTest(IntroduceParameterRefactoring.REPLACE_FIELDS_WITH_GETTERS_INACCESSIBLE, false, false, true);
  }*/

  void testRemoveParameterInHierarchy() {
    doTest(IntroduceParameterRefactoring.REPLACE_FIELDS_WITH_GETTERS_NONE, true, false, false)
  }

  /*public void testRemoveParameterWithJavadoc() {
    doTest(IntroduceParameterRefactoring.REPLACE_FIELDS_WITH_GETTERS_NONE, true, false, false);
  }*/

  void testVarargs() {   // IDEADEV-16828
    doTest(IntroduceParameterRefactoring.REPLACE_FIELDS_WITH_GETTERS_NONE, false, false, false)
  }

  void testMethodUsageInThisMethodInheritor() {
    doTest(IntroduceParameterRefactoring.REPLACE_FIELDS_WITH_GETTERS_NONE, false, false, false)
  }

  void testIntroduceConstantField() {
    doTest(IntroduceParameterRefactoring.REPLACE_FIELDS_WITH_GETTERS_NONE, false, false, false)
  }
}


