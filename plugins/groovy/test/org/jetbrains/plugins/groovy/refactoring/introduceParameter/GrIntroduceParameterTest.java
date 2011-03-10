/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
import com.intellij.psi.PsiType;
import com.intellij.psi.impl.source.PostprocessReformattingAspect;
import com.intellij.refactoring.IntroduceParameterRefactoring;
import com.intellij.testFramework.fixtures.LightCodeInsightFixtureTestCase;
import gnu.trove.TIntArrayList;
import gnu.trove.TObjectIntHashMap;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.params.GrParameter;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.TypesUtil;
import org.jetbrains.plugins.groovy.refactoring.introduce.GrIntroduceContext;
import org.jetbrains.plugins.groovy.refactoring.introduce.GrIntroduceDialog;
import org.jetbrains.plugins.groovy.refactoring.introduce.parameter.*;
import org.jetbrains.plugins.groovy.util.TestUtils;

/**
 * @author Maxim.Medvedev
 */
public class GrIntroduceParameterTest extends LightCodeInsightFixtureTestCase {
  
    protected String getBasePath() {
    return TestUtils.getTestDataPath() + "refactoring/introduceParameterGroovy/" + getTestName(true) + '/';
  }

  private boolean doTest(int replaceFieldsWithGetters, boolean removeUnusedParameters, boolean searchForSuper, boolean declareFinal) {
    return doTest(replaceFieldsWithGetters, removeUnusedParameters, searchForSuper, declareFinal, null);
  }

  private boolean doTest(final int replaceFieldsWithGetters,
                         final boolean removeUnusedParameters,
                         boolean searchForSuper,
                         final boolean declareFinal,
                         final String conflicts) {
    final String beforeGroovy = getTestName(false)+"Before.groovy";
    final String afterGroovy = getTestName(false) + "After.groovy";
    final String clazz = getTestName(false) + "MyClass.groovy";
    myFixture.configureByFiles(clazz, beforeGroovy);

    execute(replaceFieldsWithGetters, removeUnusedParameters, declareFinal, conflicts);

    PostprocessReformattingAspect.getInstance(getProject()).doPostponedFormatting();
    myFixture.getEditor().getSelectionModel().removeSelection();
    myFixture.checkResultByFile(beforeGroovy, afterGroovy, true);
    return true;
  }

  private void execute(final int replaceFieldsWithGetters,
                       final boolean removeUnusedParameters,
                       final boolean declareFinal,
                       final String conflicts) {
    CommandProcessor.getInstance().executeCommand(getProject(), new Runnable() {
      @Override
      public void run() {
        ApplicationManager.getApplication().runWriteAction(new Runnable() {
          @Override
          public void run() {
            try {
              final GrIntroduceParameterHandler hackedHandler = new GrIntroduceParameterHandler() {
                @Override
                protected GrIntroduceDialog<GrIntroduceParameterSettings> getDialog(final GrIntroduceContext context) {
                  final GrIntroduceParameterSettings hackedSettings =
                    getSettings(context, removeUnusedParameters, replaceFieldsWithGetters, declareFinal);


                  return new GrIntroduceDialog<GrIntroduceParameterSettings>() {
                    @Override
                    public GrIntroduceParameterSettings getSettings() {
                      return null;
                    }

                    @Override
                    public void show() {
                      new GrIntroduceParameterProcessor(hackedSettings, (GrIntroduceParameterContext)context).run();
                    }

                    @Override
                    public boolean isOK() {
                      return false;
                    }
                  };
                }
              };
              hackedHandler.invoke(myFixture.getProject(), myFixture.getEditor(), myFixture.getFile(), null);
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

  private GrIntroduceParameterSettings getSettings(final GrIntroduceContext context,
                                                   final boolean removeUnusedParameters,
                                                   final int replaceFieldsWithGetters,
                                                   final boolean declareFinal) {
    return new GrIntroduceParameterSettings() {
      @Override
      public boolean generateDelegate() {
        return false;
      }

      @Override
      public TIntArrayList parametersToRemove() {
        if (removeUnusedParameters) {
          final TObjectIntHashMap<GrParameter> parametersToRemove = GroovyIntroduceParameterUtil.findParametersToRemove(context);
          TIntArrayList list = new TIntArrayList(parametersToRemove.size());
          for (Object o : parametersToRemove.keys()) {
            list.add(parametersToRemove.get((GrParameter)o));
          }
          return list;
        }
        return new TIntArrayList(0);
      }

      @Override
      public int replaceFieldsWithGetters() {
        return replaceFieldsWithGetters;
      }

      @Override
      public boolean declareFinal() {
        return declareFinal;
      }

      @Override
      public boolean removeLocalVariable() {
        return false;
      }

      @Override
      public String getName() {
        return "anObject";
      }

      @Override
      public boolean replaceAllOccurrences() {
        return true;
      }

      @Override
      public PsiType getSelectedType() {
        PsiType type = context.var == null ? context.expression.getType() : context.var.getDeclaredType();
        return TypesUtil.unboxPrimitiveTypeWrapper(type);
      }
    };
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
    doTest(IntroduceParameterRefactoring.REPLACE_FIELDS_WITH_GETTERS_NONE, false, false, false, "field <b><code>Test.i</code></b> is not accessible from method <b><code>XTest.n()</code></b>. Value for introduced parameter in that method call will be incorrect.");
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
    doTest(IntroduceParameterRefactoring.REPLACE_FIELDS_WITH_GETTERS_INACCESSIBLE, false, false, false, "Parameter initializer contains <b><code>super</code></b>, but not all calls to method are in its class.");
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
    doTest(IntroduceParameterRefactoring.REPLACE_FIELDS_WITH_GETTERS_INACCESSIBLE, false, false, false, "Parameter initializer contains <b><code>super</code></b>, but not all calls to method are in its class.");
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

  public void testIncorrectArgumentList() {
    doTest(IntroduceParameterRefactoring.REPLACE_FIELDS_WITH_GETTERS_NONE, true, false, true);
  }
}
