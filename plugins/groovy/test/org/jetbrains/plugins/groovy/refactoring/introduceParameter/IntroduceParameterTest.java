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

package org.jetbrains.plugins.groovy.refactoring.introduceParameter;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pass;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.PostprocessReformattingAspect;
import com.intellij.refactoring.HelpID;
import com.intellij.refactoring.IntroduceParameterRefactoring;
import com.intellij.refactoring.introduceField.ElementToWorkOn;
import com.intellij.refactoring.introduceParameter.IntroduceParameterProcessor;
import com.intellij.refactoring.introduceParameter.Util;
import com.intellij.testFramework.fixtures.LightCodeInsightFixtureTestCase;
import gnu.trove.TIntArrayList;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.util.TestUtils;

/**
 * @author Maxim.Medvedev
 *         Date: Apr 17, 2009 5:49:35 PM
 */
public class IntroduceParameterTest extends LightCodeInsightFixtureTestCase {
  @Override
  protected String getBasePath() {
    return TestUtils.getTestDataPath() + "refactoring/introduceParameter/" + getTestName(true) + '/';
  }

  private void doTest(int replaceFieldsWithGetters, boolean removeUnusedParameters, boolean searchForSuper, boolean declareFinal)
    throws Throwable {
    doTest(replaceFieldsWithGetters, removeUnusedParameters, searchForSuper, declareFinal, null);
  }

  private void doTest(int replaceFieldsWithGetters, boolean removeUnusedParameters, boolean searchForSuper, boolean declareFinal,
                      @Nullable String conflicts)
    throws Throwable {
    final String beforeGroovy = getTestName(false)+"Before.groovy";
    final String afterGroovy = getTestName(false) + "After.groovy";
    final String javaClass = getTestName(false) + "MyClass.java";
    myFixture.configureByFiles(javaClass, beforeGroovy);
    executeRefactoring(true, replaceFieldsWithGetters, "anObject", searchForSuper, declareFinal, removeUnusedParameters, conflicts);
    PostprocessReformattingAspect.getInstance(getProject()).doPostponedFormatting();
    myFixture.checkResultByFile(beforeGroovy, afterGroovy, true);
  }

  private boolean executeRefactoring(boolean replaceAllOccurrences,
                                     int replaceFieldsWithGetters,
                                     @NonNls String parameterName,
                                     boolean searchForSuper,
                                     boolean declareFinal,
                                     final boolean removeUnusedParameters,
                                     final String conflicts) {
    boolean generateDelegate = false;
    Editor editor = myFixture.getEditor();

    final PsiFile myFile = myFixture.getFile();
    final ElementToWorkOn[] elementToWorkOn = new ElementToWorkOn[1];
    ElementToWorkOn
          .processElementToWorkOn(editor, myFile, "INtr param", HelpID.INTRODUCE_PARAMETER, getProject(), new Pass<ElementToWorkOn>() {
            @Override
            public void pass(final ElementToWorkOn e) {
              if (e == null) return;

              elementToWorkOn[0] = e;
            }
          });

    final PsiExpression expr = elementToWorkOn[0].getExpression();
    final PsiLocalVariable localVar = elementToWorkOn[0].getLocalVariable();

    PsiElement context = expr == null ? localVar : expr;
    PsiMethod method = Util.getContainingMethod(context);
    if (method == null) return false;

    final PsiMethod methodToSearchFor;
    if (searchForSuper) {
      final PsiMethod[] superMethods = method.findDeepestSuperMethods();
      methodToSearchFor = superMethods.length > 0 ? superMethods[0] : method;        //findDeepestSuperMethod();
    }
    else {
      methodToSearchFor = method;
    }

    PsiExpression initializer = expr == null ? localVar.getInitializer() : expr;
    assert initializer != null;
    TIntArrayList parametersToRemove = removeUnusedParameters ? Util.findParametersToRemove(method, initializer, null) : new TIntArrayList();
    final Project project = myFixture.getProject();
    final IntroduceParameterProcessor processor =
      new IntroduceParameterProcessor(project, method, methodToSearchFor, initializer, expr, localVar, true, parameterName,
                                      replaceAllOccurrences, replaceFieldsWithGetters, declareFinal, generateDelegate, null,
                                      parametersToRemove);

    CommandProcessor.getInstance().executeCommand(project, new Runnable() {
      @Override
      public void run() {
        ApplicationManager.getApplication().runWriteAction(new Runnable() {
          @Override
          public void run() {
            try {
              processor.run();
              if (conflicts != null) fail("Conflicts were expected");
            }
            catch (Exception e) {
              if (conflicts == null){
                e.printStackTrace();
                fail("Conflicts were not expected");
              }
              assertEquals(conflicts, e.getMessage());
            }
          }
        });
      }
    }, "introduce Parameter", null);


    editor.getSelectionModel().removeSelection();
    return true;
  }


  public void testSimpleOverridedMethod() throws Throwable {
    doTest(IntroduceParameterRefactoring.REPLACE_FIELDS_WITH_GETTERS_NONE, false, false, false);
  }

  public void testOverridedMethodWithRemoveUnusedParameters() throws Throwable {
    doTest(IntroduceParameterRefactoring.REPLACE_FIELDS_WITH_GETTERS_NONE, true, false, false);
  }

  public void testSimpleUsage() throws Throwable {
    doTest(IntroduceParameterRefactoring.REPLACE_FIELDS_WITH_GETTERS_NONE, false, false, false);
  }

  public void testMethodWithoutParams() throws Throwable {
    doTest(IntroduceParameterRefactoring.REPLACE_FIELDS_WITH_GETTERS_NONE, false, false, false);
  }

  public void testParameterSubstitution() throws Throwable {
    doTest(IntroduceParameterRefactoring.REPLACE_FIELDS_WITH_GETTERS_NONE, false, false, false);
  }

  public void testThisSubstitution() throws Throwable {
    doTest(IntroduceParameterRefactoring.REPLACE_FIELDS_WITH_GETTERS_NONE, false, false, false);
  }

  public void testThisSubstitutionInQualifier() throws Throwable {
    doTest(IntroduceParameterRefactoring.REPLACE_FIELDS_WITH_GETTERS_NONE, false, false, false, "field <b><code>Test.i</code></b> is not accessible from method <b><code>XTest.n()</code></b>. Value for introduced parameter in that method call will be incorrect.");
  }

  public void testFieldAccess() throws Throwable {
    doTest(IntroduceParameterRefactoring.REPLACE_FIELDS_WITH_GETTERS_NONE, false, false, false);
  }

  public void testMethodAccess() throws Throwable {
    doTest(IntroduceParameterRefactoring.REPLACE_FIELDS_WITH_GETTERS_NONE, false, false, false);
  }

  public void testStaticFieldAccess() throws Throwable {
    doTest(IntroduceParameterRefactoring.REPLACE_FIELDS_WITH_GETTERS_NONE, false, false, false);
  }

  public void testFieldWithGetterReplacement() throws Throwable {
    doTest(IntroduceParameterRefactoring.REPLACE_FIELDS_WITH_GETTERS_ALL, false, false, false);
  }

  public void testFieldWithInaccessibleGetterReplacement() throws Throwable {
    doTest(IntroduceParameterRefactoring.REPLACE_FIELDS_WITH_GETTERS_INACCESSIBLE, false, false, false);
  }

  public void testWeirdQualifier() throws Throwable {
    doTest(IntroduceParameterRefactoring.REPLACE_FIELDS_WITH_GETTERS_INACCESSIBLE, false, false, false);
  }

  public void testSuperInExpression() throws Throwable {
    doTest(IntroduceParameterRefactoring.REPLACE_FIELDS_WITH_GETTERS_INACCESSIBLE, false, false, false, "Parameter initializer contains <b><code>super</code></b>, but not all calls to method are in its class.");
  }

  public void testWeirdQualifierAndParameter() throws Throwable {
    doTest(IntroduceParameterRefactoring.REPLACE_FIELDS_WITH_GETTERS_INACCESSIBLE, false, false, false);
  }

  public void testImplicitSuperCall() throws Throwable {
    doTest(IntroduceParameterRefactoring.REPLACE_FIELDS_WITH_GETTERS_INACCESSIBLE, false, false, false);
  }

  public void testImplicitDefaultConstructor() throws Throwable {
    doTest(IntroduceParameterRefactoring.REPLACE_FIELDS_WITH_GETTERS_INACCESSIBLE, false, false, false);
  }

  public void testInternalSideEffect() throws Throwable {
    doTest(IntroduceParameterRefactoring.REPLACE_FIELDS_WITH_GETTERS_INACCESSIBLE, false, false, false);
  }

/*  public void testAnonymousClass() throws Throwable {
    doTest(IntroduceParameterRefactoring.REPLACE_FIELDS_WITH_GETTERS_INACCESSIBLE, false, false, false);
  }*/

  public void testSuperWithSideEffect() throws Throwable {
    doTest(IntroduceParameterRefactoring.REPLACE_FIELDS_WITH_GETTERS_INACCESSIBLE, false, false, false, "Parameter initializer contains <b><code>super</code></b>, but not all calls to method are in its class.");
  }

  public void testConflictingField() throws Throwable {
    doTest(IntroduceParameterRefactoring.REPLACE_FIELDS_WITH_GETTERS_INACCESSIBLE, false, true, false);
  }

  /*public void testParameterJavaDoc1() throws Throwable {
    doTest(IntroduceParameterRefactoring.REPLACE_FIELDS_WITH_GETTERS_INACCESSIBLE, false, false, true);
  }

  public void testParameterJavaDoc2() throws Throwable {
    doTest(IntroduceParameterRefactoring.REPLACE_FIELDS_WITH_GETTERS_INACCESSIBLE, false, false, true);
  }

  public void testParameterJavaDoc3() throws Throwable {
    doTest(IntroduceParameterRefactoring.REPLACE_FIELDS_WITH_GETTERS_INACCESSIBLE, false, false, true);
  }

  public void testParameterJavaDocBeforeVararg() throws Throwable {
    doTest(IntroduceParameterRefactoring.REPLACE_FIELDS_WITH_GETTERS_INACCESSIBLE, false, false, true);
  }*/

  public void testRemoveParameterInHierarchy() throws Throwable {
    doTest(IntroduceParameterRefactoring.REPLACE_FIELDS_WITH_GETTERS_NONE, true, false, false);
  }

  /*public void testRemoveParameterWithJavadoc() throws Throwable {
    doTest(IntroduceParameterRefactoring.REPLACE_FIELDS_WITH_GETTERS_NONE, true, false, false);
  }*/

  public void testVarargs() throws Throwable {   // IDEADEV-16828
    doTest(IntroduceParameterRefactoring.REPLACE_FIELDS_WITH_GETTERS_NONE, false, false, false);
  }

  public void testMethodUsageInThisMethodInheritor() throws Throwable {
    doTest(IntroduceParameterRefactoring.REPLACE_FIELDS_WITH_GETTERS_NONE, false, false, false);
  }
}


