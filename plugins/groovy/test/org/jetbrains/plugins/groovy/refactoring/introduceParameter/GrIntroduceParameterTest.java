// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.refactoring.introduceParameter;

import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiType;
import com.intellij.psi.impl.source.PostprocessReformattingAspect;
import com.intellij.refactoring.IntroduceParameterRefactoring;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.LightGroovyTestCase;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrStatement;
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
public class GrIntroduceParameterTest extends LightGroovyTestCase {
  @Override
  protected String getBasePath() {
    return TestUtils.getTestDataPath() + "refactoring/introduceParameterGroovy/" + getTestName(true) + "/";
  }

  private void doDelegateTest() {
    doTest(IntroduceParameterRefactoring.REPLACE_FIELDS_WITH_GETTERS_NONE, false, false, null, true);
  }

  private void doTest(int replaceFieldsWithGetters, boolean removeUnusedParameters, boolean declareFinal) {
    doTest(replaceFieldsWithGetters, removeUnusedParameters, declareFinal, null);
  }

  private void doTest(int replaceFieldsWithGetters, boolean removeUnusedParameters, boolean declareFinal, @Nullable String conflicts) {
    doTest(replaceFieldsWithGetters, removeUnusedParameters, declareFinal, conflicts, false);
  }

  private void doTest(int replaceFieldsWithGetters,
                      boolean removeUnusedParameters,
                      boolean declareFinal,
                      @Nullable String conflicts,
                      boolean generateDelegate) {
    final String beforeGroovy = getTestName(false) + "Before.groovy";
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
  }

  private void doTest(int replaceFieldsWithGetters,
                      boolean removeUnusedParameters,
                      boolean declareFinal,
                      @Nullable String conflicts,
                      boolean generateDelegate,
                      String before,
                      String after) {
    myFixture.configureByText("before.groovy", before);

    execute(replaceFieldsWithGetters, removeUnusedParameters, declareFinal, conflicts, generateDelegate, getProject(),
            myFixture.getEditor(), myFixture.getFile());

    PostprocessReformattingAspect.getInstance(getProject()).doPostponedFormatting();
    myFixture.getEditor().getSelectionModel().removeSelection();
    myFixture.checkResult(after);
  }

  private boolean exists(String sourceFilePath) {
    return new File(getTestDataPath() + "/" + sourceFilePath).exists();
  }

  public static void execute(int replaceFieldsWithGetters,
                             boolean removeUnusedParameters,
                             boolean declareFinal,
                             String conflicts,
                             boolean generateDelegate,
                             Project project,
                             Editor editor,
                             PsiFile file) {
    try {
      final GrIntroduceParameterHandler hackedHandler = new GrIntroduceParameterHandler() {
        @Override
        protected void showDialog(IntroduceParameterInfo info) {
          final GrIntroduceParameterSettings hackedSettings =
            getSettings(info, removeUnusedParameters, replaceFieldsWithGetters, declareFinal, generateDelegate);
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

  private static GrIntroduceParameterSettings getSettings(IntroduceParameterInfo context,
                                                          boolean removeUnusedParameters,
                                                          int replaceFieldsWithGetters,
                                                          boolean declareFinal,
                                                          boolean generateDelegate) {

    IntList toRemove = new IntArrayList();
    if (removeUnusedParameters) {
      Object2IntMap<GrParameter> map = GroovyIntroduceParameterUtil.findParametersToRemove(context);
      for (int i : map.values()) {
        toRemove.add(i);
      }
    }

    final GrStatement[] statements = context.getStatements();
    GrExpression expr = statements.length == 1 ? GrIntroduceHandlerBase.findExpression(statements[0]) : null;
    GrVariable var = context.getVar();
    final PsiType type = TypesUtil.unboxPrimitiveTypeWrapper(
      var != null ? var.getType() : expr != null ? expr.getType() : context.getStringPartInfo().getLiteral().getType());
    return new GrIntroduceExpressionSettingsImpl(context, "anObject", declareFinal, toRemove, generateDelegate, replaceFieldsWithGetters,
                                                 expr, var, type, var != null, var != null, true);
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
    doTest(IntroduceParameterRefactoring.REPLACE_FIELDS_WITH_GETTERS_NONE, false, false,
           "field <b><code>Test.i</code></b> is not accessible from method <b><code>XTest.n()</code></b>. Value for introduced parameter in that method call will be incorrect.");
  }

  public void testQualifiedThisSubstitution() {
    doTest(IntroduceParameterRefactoring.REPLACE_FIELDS_WITH_GETTERS_NONE, false, false, null);
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
    doTest(IntroduceParameterRefactoring.REPLACE_FIELDS_WITH_GETTERS_INACCESSIBLE, false, false,
           "Parameter initializer contains <b><code>super</code></b>, but not all calls to method are in its class");
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
    doTest(IntroduceParameterRefactoring.REPLACE_FIELDS_WITH_GETTERS_INACCESSIBLE, false, false,
           "Parameter initializer contains <b><code>super</code></b>, but not all calls to method are in its class");
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

  public void testDelegate1() { doDelegateTest(); }

  public void testDelegate2() { doDelegateTest(); }

  public void testDelegaterInSuper() { doDelegateTest(); }

  public void testClosureArg() { doTest(IntroduceParameterRefactoring.REPLACE_FIELDS_WITH_GETTERS_NONE, false, false); }

  public void testClosureArgWithEmptyArgList() { doTest(IntroduceParameterRefactoring.REPLACE_FIELDS_WITH_GETTERS_NONE, true, false); }

  public void testScriptMethod() { doTest(IntroduceParameterRefactoring.REPLACE_FIELDS_WITH_GETTERS_NONE, true, false); }

  public void testAppStatement() { doTest(IntroduceParameterRefactoring.REPLACE_FIELDS_WITH_GETTERS_NONE, false, false); }

  public void testStringPart0() {
    doTest(IntroduceParameterRefactoring.REPLACE_FIELDS_WITH_GETTERS_NONE, false, false, null, false, """
      def foo() {
          print 'a<selection>b</selection>c'
      }
      
      foo()
      """, """
             def foo(String anObject) {
                 print 'a' + anObject<caret> + 'c'
             }
             
             foo('b')
             """);
  }

  public void testIntroduceToConstructorUsedByAnonymousClass() {
    doTest(IntroduceParameterRefactoring.REPLACE_FIELDS_WITH_GETTERS_NONE, false, false, null, false, """
      class X {
        def X() {
          print <selection>2</selection>
        }
      }
      
      print new X() {
      }
      """, """
             class X {
               def X(int anObject) {
                 print anObject
               }
             }
             
             print new X(2) {
             }
             """);
  }

  public void testNullType() {
    doTest(IntroduceParameterRefactoring.REPLACE_FIELDS_WITH_GETTERS_NONE, false, false, null, false, """
      def foo() {
          def a = '4'
          <selection>print a</selection>
      }
      
      foo()
      """, """
             def foo(anObject) {
                 def a = '4'
                 anObject
             }
             
             foo(print(a))
             """);
  }

  public void testIntroduceFromLocalVar() {
    doTest(IntroduceParameterRefactoring.REPLACE_FIELDS_WITH_GETTERS_NONE, false, false, null, false, """
      def foo() {
          def <selection>var = 5</selection>
      
          print var + var
      }
      
      foo()
      """, """
             def foo(anObject) {
             
                 print anObject + anObject
             }
             
             foo(5)
             """);
  }

  public void testIntroduceFromInjection() {
    doTest(IntroduceParameterRefactoring.REPLACE_FIELDS_WITH_GETTERS_NONE, false, false, null, false, """
      def foo() {
        def bar = "bar"
        println "$<selection>bar</selection>"
      }
      """, """
             def foo(String anObject) {
               def bar = "bar"
               println "${anObject}"
             }
             """);
  }

  public void testIntroduceFromStringByCaret() {
    doTest(IntroduceParameterRefactoring.REPLACE_FIELDS_WITH_GETTERS_NONE, false, false, null, false, """
      def test() {
          createFile();
      }
      
      def createFile() {
           new File("na<caret>me").createNewFile()
      }
      """, """
             def test() {
                 createFile("name");
             }
             
             def createFile(String anObject) {
                  new File(anObject).createNewFile()
             }
             """);
  }
}