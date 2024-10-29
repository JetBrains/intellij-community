// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.refactoring.introduceParameter;

import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.PostprocessReformattingAspect;
import com.intellij.refactoring.HelpID;
import com.intellij.refactoring.IntroduceParameterRefactoring;
import com.intellij.refactoring.introduceField.ElementToWorkOn;
import com.intellij.refactoring.introduceParameter.IntroduceParameterProcessor;
import com.intellij.refactoring.introduceParameter.Util;
import com.intellij.refactoring.introduceVariable.IntroduceVariableBase;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.util.TestUtils;

/**
 * @author Maxim.Medvedev
 */
public class IntroduceParameterTest extends LightJavaCodeInsightFixtureTestCase {
  @Override
  public final String getBasePath() {
    return TestUtils.getTestDataPath() + "refactoring/introduceParameter/" + getTestName(true) + "/";
  }

  private void doTest(int replaceFieldsWithGetters,
                      boolean removeUnusedParameters,
                      boolean searchForSuper,
                      boolean declareFinal,
                      @Nullable String conflicts) {
    final String beforeGroovy = getTestName(false) + "Before.groovy";
    final String afterGroovy = getTestName(false) + "After.groovy";
    final String javaClass = getTestName(false) + "MyClass.java";
    myFixture.configureByFiles(javaClass, beforeGroovy);
    executeRefactoring(replaceFieldsWithGetters, searchForSuper, declareFinal, removeUnusedParameters, conflicts);
    PostprocessReformattingAspect.getInstance(getProject()).doPostponedFormatting();
    myFixture.checkResultByFile(beforeGroovy, afterGroovy, true);
  }

  private void doTest(int replaceFieldsWithGetters, boolean removeUnusedParameters, boolean searchForSuper, boolean declareFinal) {
    doTest(replaceFieldsWithGetters, removeUnusedParameters, searchForSuper, declareFinal, null);
  }

  private void executeRefactoring(int replaceFieldsWithGetters, boolean searchForSuper, boolean declareFinal,
                                  boolean removeUnusedParameters, String conflicts) {
    boolean generateDelegate = false;
    Editor editor = myFixture.getEditor();

    final PsiFile myFile = myFixture.getFile();
    final ElementToWorkOn[] elementToWorkOn = new ElementToWorkOn[1];
    ElementToWorkOn.processElementToWorkOn(editor, myFile, "INtr param", HelpID.INTRODUCE_PARAMETER, getProject(),
                                           new ElementToWorkOn.ElementsProcessor<>() {
                                             @Override
                                             public boolean accept(ElementToWorkOn el) {
                                               return true;
                                             }

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
    if (method == null) return;

    final PsiMethod methodToSearchFor;
    if (searchForSuper) {
      final PsiMethod[] superMethods = method.findDeepestSuperMethods();
      methodToSearchFor = superMethods.length > 0 ? superMethods[0] : method;//findDeepestSuperMethod();
    }
    else {
      methodToSearchFor = method;
    }

    PsiExpression initializer = expr == null ? localVar.getInitializer() : expr;
    assert initializer != null;
    IntList parametersToRemove = removeUnusedParameters ? Util.findParametersToRemove(method, initializer, null) : new IntArrayList();
    final Project project = myFixture.getProject();
    final IntroduceParameterProcessor processor =
      new IntroduceParameterProcessor(project, method, methodToSearchFor, initializer, expr, localVar, true, "anObject",
                                      IntroduceVariableBase.JavaReplaceChoice.ALL, replaceFieldsWithGetters, declareFinal, generateDelegate,
                                      false, null, parametersToRemove);
    try {
      processor.run();
      if (conflicts != null) fail("Conflicts were expected");
    }
    catch (Exception e) {
      if (conflicts == null) {
        e.printStackTrace();
        fail("Conflicts were not expected");
      }

      assertEquals(conflicts, e.getMessage());
    }
    editor.getSelectionModel().removeSelection();
  }

  public void testSimpleOverridedMethod() {
    doTest(IntroduceParameterRefactoring.REPLACE_FIELDS_WITH_GETTERS_NONE, false, false, false);
  }

  public void testOverridedMethodWithRemoveUnusedParameters() {
    doTest(IntroduceParameterRefactoring.REPLACE_FIELDS_WITH_GETTERS_NONE, true, false, false);
  }

  public void testSimpleUsage() {
    doTest(IntroduceParameterRefactoring.REPLACE_FIELDS_WITH_GETTERS_NONE, false, false, false);
  }

  public void testMethodWithoutParams() {
    doTest(IntroduceParameterRefactoring.REPLACE_FIELDS_WITH_GETTERS_NONE, false, false, false);
  }

  public void testParameterSubstitution() {
    doTest(IntroduceParameterRefactoring.REPLACE_FIELDS_WITH_GETTERS_NONE, false, false, false);
  }

  public void testThisSubstitution() {
    doTest(IntroduceParameterRefactoring.REPLACE_FIELDS_WITH_GETTERS_NONE, false, false, false);
  }

  public void testThisSubstitutionInQualifier() {
    doTest(IntroduceParameterRefactoring.REPLACE_FIELDS_WITH_GETTERS_NONE, false, false, false,
           "field <b><code>Test.i</code></b> is not accessible from method <b><code>XTest.n()</code></b>. Value for introduced parameter in that method call will be incorrect.");
  }

  public void testFieldAccess() {
    doTest(IntroduceParameterRefactoring.REPLACE_FIELDS_WITH_GETTERS_NONE, false, false, false);
  }

  public void testMethodAccess() {
    doTest(IntroduceParameterRefactoring.REPLACE_FIELDS_WITH_GETTERS_NONE, false, false, false);
  }

  public void testStaticFieldAccess() {
    doTest(IntroduceParameterRefactoring.REPLACE_FIELDS_WITH_GETTERS_NONE, false, false, false);
  }

  public void testFieldWithGetterReplacement() {
    doTest(IntroduceParameterRefactoring.REPLACE_FIELDS_WITH_GETTERS_ALL, false, false, false);
  }

  public void testFieldWithInaccessibleGetterReplacement() {
    doTest(IntroduceParameterRefactoring.REPLACE_FIELDS_WITH_GETTERS_INACCESSIBLE, false, false, false);
  }

  public void testWeirdQualifier() {
    doTest(IntroduceParameterRefactoring.REPLACE_FIELDS_WITH_GETTERS_INACCESSIBLE, false, false, false);
  }

  public void testSuperInExpression() {
    doTest(IntroduceParameterRefactoring.REPLACE_FIELDS_WITH_GETTERS_INACCESSIBLE, false, false, false,
           "Parameter initializer contains <b><code>super</code></b>, but not all calls to method are in its class");
  }

  public void testWeirdQualifierAndParameter() {
    doTest(IntroduceParameterRefactoring.REPLACE_FIELDS_WITH_GETTERS_INACCESSIBLE, false, false, false);
  }

  public void testImplicitSuperCall() {
    doTest(IntroduceParameterRefactoring.REPLACE_FIELDS_WITH_GETTERS_INACCESSIBLE, false, false, false);
  }

  public void testImplicitDefaultConstructor() {
    doTest(IntroduceParameterRefactoring.REPLACE_FIELDS_WITH_GETTERS_INACCESSIBLE, false, false, false);
  }

  public void testInternalSideEffect() {
    doTest(IntroduceParameterRefactoring.REPLACE_FIELDS_WITH_GETTERS_INACCESSIBLE, false, false, false);
  }
  
/*  public void testAnonymousClass() {
    doTest(IntroduceParameterRefactoring.REPLACE_FIELDS_WITH_GETTERS_INACCESSIBLE, false, false, false);
  }*/

  public void testSuperWithSideEffect() {
    doTest(IntroduceParameterRefactoring.REPLACE_FIELDS_WITH_GETTERS_INACCESSIBLE, false, false, false,
           "Parameter initializer contains <b><code>super</code></b>, but not all calls to method are in its class");
  }

  public void testConflictingField() {
    doTest(IntroduceParameterRefactoring.REPLACE_FIELDS_WITH_GETTERS_INACCESSIBLE, false, true, false);
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

  public void testRemoveParameterInHierarchy() {
    doTest(IntroduceParameterRefactoring.REPLACE_FIELDS_WITH_GETTERS_NONE, true, false, false);
  }
  
  /*public void testRemoveParameterWithJavadoc() {
    doTest(IntroduceParameterRefactoring.REPLACE_FIELDS_WITH_GETTERS_NONE, true, false, false);
  }*/

  public void testVarargs() {   // IDEADEV-16828
    doTest(IntroduceParameterRefactoring.REPLACE_FIELDS_WITH_GETTERS_NONE, false, false, false);
  }

  public void testMethodUsageInThisMethodInheritor() {
    doTest(IntroduceParameterRefactoring.REPLACE_FIELDS_WITH_GETTERS_NONE, false, false, false);
  }

  public void testIntroduceConstantField() {
    doTest(IntroduceParameterRefactoring.REPLACE_FIELDS_WITH_GETTERS_NONE, false, false, false);
  }
}