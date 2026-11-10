// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.refactoring.introduceParameter;

import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiLocalVariable;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.impl.source.PostprocessReformattingAspect;
import com.intellij.refactoring.HelpID;
import com.intellij.refactoring.IntroduceParameterRefactoring;
import com.intellij.refactoring.introduceField.ElementToWorkOn;
import com.intellij.refactoring.introduceParameter.IntroduceParameterProcessor;
import com.intellij.refactoring.introduceParameter.Util;
import com.intellij.refactoring.introduceVariable.IntroduceVariableBase;
import com.intellij.testFramework.EdtTestUtil;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.util.TestUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaCodeInsightFixture;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * @author Maxim.Medvedev
 */
@TestApplication
public class IntroduceParameterTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture();
  private final TestFixture<LightJavaCodeInsightTestFixture> codeInsightFixture = lightJavaCodeInsightFixture(projectFixture);

  @BeforeEach
  void setUpTestDataPath() {
    codeInsightFixture.get().setTestDataPath(TestUtils.getAbsoluteTestDataPath() + "refactoring/introduceParameter/" + codeInsightFixture.get().getTestName(true) + "/");
  }

  private void doTest(int replaceFieldsWithGetters,
                      boolean removeUnusedParameters,
                      boolean searchForSuper,
                      boolean declareFinal,
                      @Nullable String conflicts) {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    final String beforeGroovy = myFixture.getTestName(false) + "Before.groovy";
    final String afterGroovy = myFixture.getTestName(false) + "After.groovy";
    final String javaClass = myFixture.getTestName(false) + "MyClass.java";
    myFixture.configureByFiles(javaClass, beforeGroovy);
    EdtTestUtil.runInEdtAndWait(() -> {
      executeRefactoring(replaceFieldsWithGetters, searchForSuper, declareFinal, removeUnusedParameters, conflicts);
      PostprocessReformattingAspect.getInstance(myFixture.getProject()).doPostponedFormatting();
      myFixture.checkResultByFile(beforeGroovy, afterGroovy, true);
    });
  }

  private void doTest(int replaceFieldsWithGetters, boolean removeUnusedParameters, boolean searchForSuper, boolean declareFinal) {
    doTest(replaceFieldsWithGetters, removeUnusedParameters, searchForSuper, declareFinal, null);
  }

  private void executeRefactoring(int replaceFieldsWithGetters, boolean searchForSuper, boolean declareFinal,
                                  boolean removeUnusedParameters, String conflicts) {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    boolean generateDelegate = false;
    Editor editor = myFixture.getEditor();

    final PsiFile myFile = myFixture.getFile();
    final ElementToWorkOn[] elementToWorkOn = new ElementToWorkOn[1];
    ElementToWorkOn.processElementToWorkOn(editor, myFile, "INtr param", HelpID.INTRODUCE_PARAMETER, myFixture.getProject(),
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
    assertNotNull(initializer);
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

  @Test
  public void testSimpleOverridedMethod() {
    doTest(IntroduceParameterRefactoring.REPLACE_FIELDS_WITH_GETTERS_NONE, false, false, false);
  }

  @Test
  public void testOverridedMethodWithRemoveUnusedParameters() {
    doTest(IntroduceParameterRefactoring.REPLACE_FIELDS_WITH_GETTERS_NONE, true, false, false);
  }

  @Test
  public void testSimpleUsage() {
    doTest(IntroduceParameterRefactoring.REPLACE_FIELDS_WITH_GETTERS_NONE, false, false, false);
  }

  @Test
  public void testMethodWithoutParams() {
    doTest(IntroduceParameterRefactoring.REPLACE_FIELDS_WITH_GETTERS_NONE, false, false, false);
  }

  @Test
  public void testParameterSubstitution() {
    doTest(IntroduceParameterRefactoring.REPLACE_FIELDS_WITH_GETTERS_NONE, false, false, false);
  }

  @Test
  public void testThisSubstitution() {
    doTest(IntroduceParameterRefactoring.REPLACE_FIELDS_WITH_GETTERS_NONE, false, false, false);
  }

  @Test
  public void testThisSubstitutionInQualifier() {
    doTest(IntroduceParameterRefactoring.REPLACE_FIELDS_WITH_GETTERS_NONE, false, false, false,
           "field <b><code>Test.i</code></b> is not accessible from method <b><code>XTest.n()</code></b>. Value for introduced parameter in that method call will be incorrect.");
  }

  @Test
  public void testFieldAccess() {
    doTest(IntroduceParameterRefactoring.REPLACE_FIELDS_WITH_GETTERS_NONE, false, false, false);
  }

  @Test
  public void testMethodAccess() {
    doTest(IntroduceParameterRefactoring.REPLACE_FIELDS_WITH_GETTERS_NONE, false, false, false);
  }

  @Test
  public void testStaticFieldAccess() {
    doTest(IntroduceParameterRefactoring.REPLACE_FIELDS_WITH_GETTERS_NONE, false, false, false);
  }

  @Test
  public void testFieldWithGetterReplacement() {
    doTest(IntroduceParameterRefactoring.REPLACE_FIELDS_WITH_GETTERS_ALL, false, false, false);
  }

  @Test
  public void testFieldWithInaccessibleGetterReplacement() {
    doTest(IntroduceParameterRefactoring.REPLACE_FIELDS_WITH_GETTERS_INACCESSIBLE, false, false, false);
  }

  @Test
  public void testWeirdQualifier() {
    doTest(IntroduceParameterRefactoring.REPLACE_FIELDS_WITH_GETTERS_INACCESSIBLE, false, false, false);
  }

  @Test
  public void testSuperInExpression() {
    doTest(IntroduceParameterRefactoring.REPLACE_FIELDS_WITH_GETTERS_INACCESSIBLE, false, false, false,
           "Parameter initializer contains <b><code>super</code></b>, but not all calls to method are in its class");
  }

  @Test
  public void testWeirdQualifierAndParameter() {
    doTest(IntroduceParameterRefactoring.REPLACE_FIELDS_WITH_GETTERS_INACCESSIBLE, false, false, false);
  }

  @Test
  public void testImplicitSuperCall() {
    doTest(IntroduceParameterRefactoring.REPLACE_FIELDS_WITH_GETTERS_INACCESSIBLE, false, false, false);
  }

  @Test
  public void testImplicitDefaultConstructor() {
    doTest(IntroduceParameterRefactoring.REPLACE_FIELDS_WITH_GETTERS_INACCESSIBLE, false, false, false);
  }

  @Test
  public void testInternalSideEffect() {
    doTest(IntroduceParameterRefactoring.REPLACE_FIELDS_WITH_GETTERS_INACCESSIBLE, false, false, false);
  }
  
/*  public void testAnonymousClass() {
    doTest(IntroduceParameterRefactoring.REPLACE_FIELDS_WITH_GETTERS_INACCESSIBLE, false, false, false);
  }*/

  @Test
  public void testSuperWithSideEffect() {
    doTest(IntroduceParameterRefactoring.REPLACE_FIELDS_WITH_GETTERS_INACCESSIBLE, false, false, false,
           "Parameter initializer contains <b><code>super</code></b>, but not all calls to method are in its class");
  }

  @Test
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

  @Test
  public void testRemoveParameterInHierarchy() {
    doTest(IntroduceParameterRefactoring.REPLACE_FIELDS_WITH_GETTERS_NONE, true, false, false);
  }
  
  /*public void testRemoveParameterWithJavadoc() {
    doTest(IntroduceParameterRefactoring.REPLACE_FIELDS_WITH_GETTERS_NONE, true, false, false);
  }*/

  @Test
  public void testVarargs() {
    // IDEADEV-16828
     doTest(IntroduceParameterRefactoring.REPLACE_FIELDS_WITH_GETTERS_NONE, false, false, false);
  }

  @Test
  public void testMethodUsageInThisMethodInheritor() {
    doTest(IntroduceParameterRefactoring.REPLACE_FIELDS_WITH_GETTERS_NONE, false, false, false);
  }

  @Test
  public void testIntroduceConstantField() {
    doTest(IntroduceParameterRefactoring.REPLACE_FIELDS_WITH_GETTERS_NONE, false, false, false);
  }
}