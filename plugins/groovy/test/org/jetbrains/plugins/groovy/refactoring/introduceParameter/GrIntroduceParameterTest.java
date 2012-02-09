/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package org.jetbrains.plugins.groovy.refactoring.introduceParameter;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiType;
import com.intellij.psi.impl.source.PostprocessReformattingAspect;
import com.intellij.refactoring.IntroduceParameterRefactoring;
import com.intellij.testFramework.fixtures.LightCodeInsightFixtureTestCase;
import gnu.trove.TIntArrayList;
import gnu.trove.TObjectIntHashMap;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariable;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.params.GrParameter;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.TypesUtil;
import org.jetbrains.plugins.groovy.refactoring.introduce.GrIntroduceHandlerBase;
import org.jetbrains.plugins.groovy.refactoring.introduce.parameter.*;
import org.jetbrains.plugins.groovy.util.TestUtils;

import java.io.File;

/**
 * @author Maxim.Medvedev
 */
public class GrIntroduceParameterTest extends LightCodeInsightFixtureTestCase {

  protected String getBasePath() {
    return TestUtils.getTestDataPath() + "refactoring/introduceParameterGroovy/" + getTestName(true) + '/';
  }

  private void doDelegateTest() {
    doTest(IntroduceParameterRefactoring.REPLACE_FIELDS_WITH_GETTERS_NONE, false, false, null, true);
  }

  private boolean doTest(int replaceFieldsWithGetters, boolean removeUnusedParameters, boolean declareFinal) {
    return doTest(replaceFieldsWithGetters, removeUnusedParameters, declareFinal, null);
  }

  private boolean doTest(final int replaceFieldsWithGetters,
                         final boolean removeUnusedParameters,
                         final boolean declareFinal,
                         @Nullable final String conflicts) {
    return doTest(replaceFieldsWithGetters, removeUnusedParameters, declareFinal, conflicts, false);
  }

  private boolean doTest(final int replaceFieldsWithGetters,
                         final boolean removeUnusedParameters,
                         final boolean declareFinal,
                         @Nullable final String conflicts,
                         final boolean generateDelegate) {
    final String beforeGroovy = getTestName(false)+"Before.groovy";
    final String afterGroovy = getTestName(false) + "After.groovy";
    final String clazz = getTestName(false) + "MyClass.groovy";
    final String afterClazz = getTestName(false) + "MyClass_after.groovy";

    final boolean beforeExists = exists(beforeGroovy);
    if (beforeExists) {
      myFixture.copyFileToProject(beforeGroovy);
    }
    myFixture.configureByFile(clazz);

    execute(replaceFieldsWithGetters, removeUnusedParameters, declareFinal, conflicts, generateDelegate, getProject(),
            myFixture.getEditor(), myFixture.getFile());

    PostprocessReformattingAspect.getInstance(getProject()).doPostponedFormatting();
    myFixture.getEditor().getSelectionModel().removeSelection();
    if (beforeExists) {
      myFixture.checkResultByFile(beforeGroovy, afterGroovy, true);
    }

    if (exists(afterClazz)) {
      myFixture.checkResultByFile(afterClazz);
    }
    return true;
  }


  private boolean exists(String sourceFilePath) {
    File file = new File(getTestDataPath() + "/" + sourceFilePath);
    return file.exists();
  }

  static void execute(final int replaceFieldsWithGetters,
                      final boolean removeUnusedParameters,
                      final boolean declareFinal,
                      final String conflicts,
                      final boolean generateDelegate,
                      final Project project,
                      final Editor editor,
                      final PsiFile file) {
    CommandProcessor.getInstance().executeCommand(project, new Runnable() {
      @Override
      public void run() {
        ApplicationManager.getApplication().runWriteAction(new Runnable() {
          @Override
          public void run() {
            try {
              final GrIntroduceParameterHandler hackedHandler = new GrIntroduceParameterHandler() {
                @Override
                protected void showDialog(IntroduceParameterInfo info) {
                  final GrIntroduceExpressionSettings hackedSettings = getSettings(info, removeUnusedParameters, replaceFieldsWithGetters, declareFinal, generateDelegate);
                  if (info.getToReplaceIn() instanceof GrMethod) {
                    new GrIntroduceParameterProcessor(hackedSettings).run();
                  }
                  else {
                    new GrIntroduceClosureParameterProcessor(hackedSettings).run();
                  }
                }
              };
              hackedHandler.invoke(project, editor, file, null);
              if (conflicts != null) fail("Conflicts were expected");
            }
            catch (Exception e) {
              if (conflicts == null) {
                e.printStackTrace();
                fail("Conflicts were not expected");
              }
              assertEquals(conflicts, e.getMessage());
            }
          }
        });
      }
    }, "introduce Parameter", null);
  }

  private static GrIntroduceExpressionSettings getSettings(final IntroduceParameterInfo context,
                                                          final boolean removeUnusedParameters,
                                                          final int replaceFieldsWithGetters,
                                                          final boolean declareFinal,
                                                          final boolean generateDelegate) {

    TIntArrayList toRemove = new TIntArrayList();
    if (removeUnusedParameters) {
      final TObjectIntHashMap<GrParameter> map = GroovyIntroduceParameterUtil.findParametersToRemove(context);
      for (int i : map.getValues()) {
        toRemove.add(i);
      }
    }
    GrExpression expr = GrIntroduceHandlerBase.findExpression(context.getStatements()[0]);
    GrVariable var = GrIntroduceHandlerBase.findVariable(context.getStatements()[0]);
    final PsiType type = TypesUtil.unboxPrimitiveTypeWrapper(var == null ? expr.getType() : var.getType());
    return new GrIntroduceExpressionSettingsImpl(context, "anObject", declareFinal, toRemove, generateDelegate, replaceFieldsWithGetters, expr, var, type);
  }
  
  
  public void testSimpleOverridedMethod() {
    doTest(IntroduceParameterRefactoring.REPLACE_FIELDS_WITH_GETTERS_NONE, false, false);
  }

  public void testOverridedMethodWithRemoveUnusedParameters() {
    doTest(IntroduceParameterRefactoring.REPLACE_FIELDS_WITH_GETTERS_NONE, true, false);
  }

  public void testSimpleUsage() {
    doTest(IntroduceParameterRefactoring.REPLACE_FIELDS_WITH_GETTERS_NONE, false, false);
  }

  public void testMethodWithoutParams() {
    doTest(IntroduceParameterRefactoring.REPLACE_FIELDS_WITH_GETTERS_NONE, false, false);
  }

  public void testParameterSubstitution() {
    doTest(IntroduceParameterRefactoring.REPLACE_FIELDS_WITH_GETTERS_NONE, false, false);
  }

  public void testThisSubstitution() {
    doTest(IntroduceParameterRefactoring.REPLACE_FIELDS_WITH_GETTERS_NONE, false, false);
  }

  public void testThisSubstitutionInQualifier() {
    doTest(IntroduceParameterRefactoring.REPLACE_FIELDS_WITH_GETTERS_NONE, false, false, "field <b><code>Test.i</code></b> is not accessible from method <b><code>XTest.n()</code></b>. Value for introduced parameter in that method call will be incorrect.");
  }

  public void testFieldAccess() {
    doTest(IntroduceParameterRefactoring.REPLACE_FIELDS_WITH_GETTERS_NONE, false, false);
  }

  public void testMethodAccess() {
    doTest(IntroduceParameterRefactoring.REPLACE_FIELDS_WITH_GETTERS_NONE, false, false);
  }

  public void testStaticFieldAccess() {
    doTest(IntroduceParameterRefactoring.REPLACE_FIELDS_WITH_GETTERS_NONE, false, false);
  }

  public void testFieldWithGetterReplacement() {
    doTest(IntroduceParameterRefactoring.REPLACE_FIELDS_WITH_GETTERS_ALL, false, false);
  }

  public void testFieldWithInaccessibleGetterReplacement() {
    doTest(IntroduceParameterRefactoring.REPLACE_FIELDS_WITH_GETTERS_INACCESSIBLE, false, false);
  }

  public void testWeirdQualifier() {
    doTest(IntroduceParameterRefactoring.REPLACE_FIELDS_WITH_GETTERS_INACCESSIBLE, false, false);
  }

  public void testSuperInExpression() {
    doTest(IntroduceParameterRefactoring.REPLACE_FIELDS_WITH_GETTERS_INACCESSIBLE, false, false, "Parameter initializer contains <b><code>super</code></b>, but not all calls to method are in its class.");
  }

  public void testWeirdQualifierAndParameter() {
    doTest(IntroduceParameterRefactoring.REPLACE_FIELDS_WITH_GETTERS_INACCESSIBLE, false, false);
  }

  public void testImplicitSuperCall() {
    doTest(IntroduceParameterRefactoring.REPLACE_FIELDS_WITH_GETTERS_INACCESSIBLE, false, false);
  }

  public void testImplicitDefaultConstructor() {
    doTest(IntroduceParameterRefactoring.REPLACE_FIELDS_WITH_GETTERS_INACCESSIBLE, false, false);
  }

  public void testInternalSideEffect() {
    doTest(IntroduceParameterRefactoring.REPLACE_FIELDS_WITH_GETTERS_INACCESSIBLE, false, false);
  }

/*  public void testAnonymousClass() {
    doTest(IntroduceParameterRefactoring.REPLACE_FIELDS_WITH_GETTERS_INACCESSIBLE, false, false, false);
  }*/

  public void testSuperWithSideEffect() {
    doTest(IntroduceParameterRefactoring.REPLACE_FIELDS_WITH_GETTERS_INACCESSIBLE, false, false, "Parameter initializer contains <b><code>super</code></b>, but not all calls to method are in its class.");
  }

  public void testConflictingField() {
    doTest(IntroduceParameterRefactoring.REPLACE_FIELDS_WITH_GETTERS_INACCESSIBLE, false, false);
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
    doTest(IntroduceParameterRefactoring.REPLACE_FIELDS_WITH_GETTERS_NONE, true, false);
  }

  /*public void testRemoveParameterWithJavadoc() {
    doTest(IntroduceParameterRefactoring.REPLACE_FIELDS_WITH_GETTERS_NONE, true, false, false);
  }*/

  public void testVarargs() {   // IDEADEV-16828
    doTest(IntroduceParameterRefactoring.REPLACE_FIELDS_WITH_GETTERS_NONE, false, false);
  }

  public void testMethodUsageInThisMethodInheritor() {
    doTest(IntroduceParameterRefactoring.REPLACE_FIELDS_WITH_GETTERS_NONE, false, false);
  }

  public void testIncorrectArgumentList() {
    doTest(IntroduceParameterRefactoring.REPLACE_FIELDS_WITH_GETTERS_NONE, true, true);
  }

  public void testClosure() {
    doTest(IntroduceParameterRefactoring.REPLACE_FIELDS_WITH_GETTERS_NONE, false, false);
  }

  public void testDelegate1() {doDelegateTest();}
  public void testDelegate2() {doDelegateTest();}
  
  public void testDelegaterInSuper() {doDelegateTest();}

  public void testClosureArg() {doTest(IntroduceParameterRefactoring.REPLACE_FIELDS_WITH_GETTERS_NONE, false, false);}
  public void testClosureArgWithEmptyArgList() {doTest(IntroduceParameterRefactoring.REPLACE_FIELDS_WITH_GETTERS_NONE, true, false);}

  public void testScriptMethod() {doTest(IntroduceParameterRefactoring.REPLACE_FIELDS_WITH_GETTERS_NONE, true, false);}
}
