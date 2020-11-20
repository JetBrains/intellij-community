// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.refactoring.introduceParameter

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiType
import com.intellij.psi.impl.source.PostprocessReformattingAspect
import com.intellij.refactoring.IntroduceParameterRefactoring
import it.unimi.dsi.fastutil.ints.IntArrayList
import it.unimi.dsi.fastutil.objects.Object2IntMap
import org.jetbrains.annotations.Nullable
import org.jetbrains.plugins.groovy.LightGroovyTestCase
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrStatement
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariable
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression
import org.jetbrains.plugins.groovy.lang.psi.api.statements.params.GrParameter
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.TypesUtil
import org.jetbrains.plugins.groovy.refactoring.introduce.GrIntroduceHandlerBase
import org.jetbrains.plugins.groovy.refactoring.introduce.parameter.*
import org.jetbrains.plugins.groovy.util.TestUtils
import org.junit.Assert

/**
 * @author Maxim.Medvedev
 */
class GrIntroduceParameterTest extends LightGroovyTestCase {
  protected String getBasePath() {
    return TestUtils.getTestDataPath() + "refactoring/introduceParameterGroovy/" + getTestName(true) + '/'
  }

  private void doDelegateTest() {
    doTest(IntroduceParameterRefactoring.REPLACE_FIELDS_WITH_GETTERS_NONE, false, false, null, true)
  }

  private boolean doTest(int replaceFieldsWithGetters, boolean removeUnusedParameters, boolean declareFinal) {
    return doTest(replaceFieldsWithGetters, removeUnusedParameters, declareFinal, null)
  }

  private boolean doTest(final int replaceFieldsWithGetters,
                         final boolean removeUnusedParameters,
                         final boolean declareFinal,
                         @Nullable final String conflicts) {
    return doTest(replaceFieldsWithGetters, removeUnusedParameters, declareFinal, conflicts, false)
  }

  private boolean doTest(final int replaceFieldsWithGetters,
                         final boolean removeUnusedParameters,
                         final boolean declareFinal,
                         @Nullable final String conflicts,
                         final boolean generateDelegate) {
    final String beforeGroovy = getTestName(false)+"Before.groovy"
    final String afterGroovy = getTestName(false) + "After.groovy"
    final String clazz = getTestName(false) + "MyClass.groovy"
    final String afterClazz = getTestName(false) + "MyClass_after.groovy"

    final boolean beforeExists = exists(beforeGroovy)
    if (beforeExists) {
      myFixture.copyFileToProject(beforeGroovy)
    }
    myFixture.configureByFile(clazz)

    execute(replaceFieldsWithGetters, removeUnusedParameters, declareFinal, conflicts, generateDelegate, getProject(),
            myFixture.getEditor(), myFixture.getFile())

    PostprocessReformattingAspect.getInstance(getProject()).doPostponedFormatting()
    myFixture.getEditor().getSelectionModel().removeSelection()
    if (beforeExists) {
      myFixture.checkResultByFile(beforeGroovy, afterGroovy, true)
    }

    if (exists(afterClazz)) {
      myFixture.checkResultByFile(afterClazz)
    }
    return true
  }

  private void doTest(final int replaceFieldsWithGetters,
                         final boolean removeUnusedParameters,
                         final boolean declareFinal,
                         @Nullable final String conflicts,
                         final boolean generateDelegate, String before, String after) {
    myFixture.configureByText('before.groovy', before)

    execute(replaceFieldsWithGetters, removeUnusedParameters, declareFinal, conflicts, generateDelegate, getProject(),
            myFixture.getEditor(), myFixture.getFile())

    PostprocessReformattingAspect.getInstance(getProject()).doPostponedFormatting()
    myFixture.getEditor().getSelectionModel().removeSelection()

    myFixture.checkResult(after)
  }


  private boolean exists(String sourceFilePath) {
    File file = new File(getTestDataPath() + "/" + sourceFilePath)
    return file.exists()
  }

  static void execute(final int replaceFieldsWithGetters,
                      final boolean removeUnusedParameters,
                      final boolean declareFinal,
                      final String conflicts,
                      final boolean generateDelegate,
                      final Project project,
                      final Editor editor,
                      final PsiFile file) {
    try {
      final GrIntroduceParameterHandler hackedHandler = new GrIntroduceParameterHandler() {
        @Override
        protected void showDialog(IntroduceParameterInfo info) {
          final GrIntroduceParameterSettings hackedSettings =
            getSettings(info, removeUnusedParameters, replaceFieldsWithGetters, declareFinal, generateDelegate)
          if (info.getToReplaceIn() instanceof GrMethod) {
            new GrIntroduceParameterProcessor(hackedSettings).run()
          }
          else {
            new GrIntroduceClosureParameterProcessor(hackedSettings).run()
          }
        }
      }
      hackedHandler.invoke(project, editor, file, null)
      if (conflicts != null) fail("Conflicts were expected")
    }
    catch (Exception e) {
      if (conflicts == null) {
        e.printStackTrace()
        fail("Conflicts were not expected")
      }
      Assert.assertEquals(conflicts, e.getMessage())
    }
  }

  private static GrIntroduceParameterSettings getSettings(final IntroduceParameterInfo context,
                                                          final boolean removeUnusedParameters,
                                                          final int replaceFieldsWithGetters,
                                                          final boolean declareFinal,
                                                          final boolean generateDelegate) {

    IntArrayList toRemove = new IntArrayList()
    if (removeUnusedParameters) {
      Object2IntMap<GrParameter> map = GroovyIntroduceParameterUtil.findParametersToRemove(context)
      for (int i : map.values()) {
        toRemove.add(i)
      }
    }

    final GrStatement[] statements = context.getStatements()
    GrExpression expr = statements.length == 1 ? GrIntroduceHandlerBase.findExpression(statements[0]) : null
    GrVariable var = context.var
    final PsiType type = TypesUtil.unboxPrimitiveTypeWrapper(var != null ? var.getType() : expr != null ? expr.getType() : context.stringPartInfo.literal.type)
    return new GrIntroduceExpressionSettingsImpl(context, "anObject", declareFinal, toRemove, generateDelegate, replaceFieldsWithGetters,
                                                 expr, var, type, var !=null, var != null, true)
  }


  void testSimpleOverridedMethod() {
    doTest(IntroduceParameterRefactoring.REPLACE_FIELDS_WITH_GETTERS_NONE, false, false)
  }

  void testOverridedMethodWithRemoveUnusedParameters() {
    doTest(IntroduceParameterRefactoring.REPLACE_FIELDS_WITH_GETTERS_NONE, true, false)
  }

  void testSimpleUsage() {
    doTest(IntroduceParameterRefactoring.REPLACE_FIELDS_WITH_GETTERS_NONE, false, false)
  }

  void testMethodWithoutParams() {
    doTest(IntroduceParameterRefactoring.REPLACE_FIELDS_WITH_GETTERS_NONE, false, false)
  }

  void testParameterSubstitution() {
    doTest(IntroduceParameterRefactoring.REPLACE_FIELDS_WITH_GETTERS_NONE, false, false)
  }

  void testThisSubstitution() {
    doTest(IntroduceParameterRefactoring.REPLACE_FIELDS_WITH_GETTERS_NONE, false, false)
  }

  void testThisSubstitutionInQualifier() {
    doTest(IntroduceParameterRefactoring.REPLACE_FIELDS_WITH_GETTERS_NONE, false, false, "field <b><code>Test.i</code></b> is not accessible from method <b><code>XTest.n()</code></b>. Value for introduced parameter in that method call will be incorrect.")
  }

  void testQualifiedThisSubstitution() {
    doTest(IntroduceParameterRefactoring.REPLACE_FIELDS_WITH_GETTERS_NONE, false, false, null)
  }

  void testFieldAccess() {
    doTest(IntroduceParameterRefactoring.REPLACE_FIELDS_WITH_GETTERS_NONE, false, false)
  }

  void testMethodAccess() {
    doTest(IntroduceParameterRefactoring.REPLACE_FIELDS_WITH_GETTERS_NONE, false, false)
  }

  void testStaticFieldAccess() {
    doTest(IntroduceParameterRefactoring.REPLACE_FIELDS_WITH_GETTERS_NONE, false, false)
  }

  void testFieldWithGetterReplacement() {
    doTest(IntroduceParameterRefactoring.REPLACE_FIELDS_WITH_GETTERS_ALL, false, false)
  }

  void testFieldWithInaccessibleGetterReplacement() {
    doTest(IntroduceParameterRefactoring.REPLACE_FIELDS_WITH_GETTERS_INACCESSIBLE, false, false)
  }

  void testWeirdQualifier() {
    doTest(IntroduceParameterRefactoring.REPLACE_FIELDS_WITH_GETTERS_INACCESSIBLE, false, false)
  }

  void testSuperInExpression() {
    doTest(IntroduceParameterRefactoring.REPLACE_FIELDS_WITH_GETTERS_INACCESSIBLE, false, false, "Parameter initializer contains <b><code>super</code></b>, but not all calls to method are in its class")
  }

  void testWeirdQualifierAndParameter() {
    doTest(IntroduceParameterRefactoring.REPLACE_FIELDS_WITH_GETTERS_INACCESSIBLE, false, false)
  }

  void testImplicitSuperCall() {
    doTest(IntroduceParameterRefactoring.REPLACE_FIELDS_WITH_GETTERS_INACCESSIBLE, false, false)
  }

  void testImplicitDefaultConstructor() {
    doTest(IntroduceParameterRefactoring.REPLACE_FIELDS_WITH_GETTERS_INACCESSIBLE, false, false)
  }

  void testInternalSideEffect() {
    doTest(IntroduceParameterRefactoring.REPLACE_FIELDS_WITH_GETTERS_INACCESSIBLE, false, false)
  }

/*  public void testAnonymousClass() {
    doTest(IntroduceParameterRefactoring.REPLACE_FIELDS_WITH_GETTERS_INACCESSIBLE, false, false, false);
  }*/

  void testSuperWithSideEffect() {
    doTest(IntroduceParameterRefactoring.REPLACE_FIELDS_WITH_GETTERS_INACCESSIBLE, false, false, "Parameter initializer contains <b><code>super</code></b>, but not all calls to method are in its class")
  }

  void testConflictingField() {
    doTest(IntroduceParameterRefactoring.REPLACE_FIELDS_WITH_GETTERS_INACCESSIBLE, false, false)
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
    doTest(IntroduceParameterRefactoring.REPLACE_FIELDS_WITH_GETTERS_NONE, true, false)
  }

  /*public void testRemoveParameterWithJavadoc() {
    doTest(IntroduceParameterRefactoring.REPLACE_FIELDS_WITH_GETTERS_NONE, true, false, false);
  }*/

  void testVarargs() {   // IDEADEV-16828
    doTest(IntroduceParameterRefactoring.REPLACE_FIELDS_WITH_GETTERS_NONE, false, false)
  }

  void testMethodUsageInThisMethodInheritor() {
    doTest(IntroduceParameterRefactoring.REPLACE_FIELDS_WITH_GETTERS_NONE, false, false)
  }

  void testIncorrectArgumentList() {
    doTest(IntroduceParameterRefactoring.REPLACE_FIELDS_WITH_GETTERS_NONE, true, true)
  }

  void testClosure() {
    doTest(IntroduceParameterRefactoring.REPLACE_FIELDS_WITH_GETTERS_NONE, false, false)
  }

  void testDelegate1() { doDelegateTest() }

  void testDelegate2() { doDelegateTest() }

  void testDelegaterInSuper() { doDelegateTest() }

  void testClosureArg() { doTest(IntroduceParameterRefactoring.REPLACE_FIELDS_WITH_GETTERS_NONE, false, false) }

  void testClosureArgWithEmptyArgList() { doTest(IntroduceParameterRefactoring.REPLACE_FIELDS_WITH_GETTERS_NONE, true, false) }

  void testScriptMethod() { doTest(IntroduceParameterRefactoring.REPLACE_FIELDS_WITH_GETTERS_NONE, true, false) }

  void testAppStatement() { doTest(IntroduceParameterRefactoring.REPLACE_FIELDS_WITH_GETTERS_NONE, false, false) }

  void testStringPart0() {
    doTest(IntroduceParameterRefactoring.REPLACE_FIELDS_WITH_GETTERS_NONE, false, false, null, false, '''\
def foo() {
    print 'a<selection>b</selection>c'
}

foo()
''', '''\
def foo(String anObject) {
    print 'a' + anObject<caret> + 'c'
}

foo('b')
''')
  }

  void testIntroduceToConstructorUsedByAnonymousClass() {
    doTest(IntroduceParameterRefactoring.REPLACE_FIELDS_WITH_GETTERS_NONE, false, false, null, false, '''\
class X {
  def X() {
    print <selection>2</selection>
  }
}

print new X() {
}
''', '''\
class X {
  def X(int anObject) {
    print anObject
  }
}

print new X(2) {
}
''')
  }


  void testNullType() {
    doTest(IntroduceParameterRefactoring.REPLACE_FIELDS_WITH_GETTERS_NONE, false, false, null, false, '''\
def foo() {
    def a = '4'
    <selection>print a</selection>
}

foo()
''', '''\
def foo(anObject) {
    def a = '4'
    anObject
}

foo(print(a))
''')
  }

  void testIntroduceFromLocalVar() {
    doTest(IntroduceParameterRefactoring.REPLACE_FIELDS_WITH_GETTERS_NONE, false, false, null, false, '''\
def foo() {
    def <selection>var = 5</selection>

    print var + var
}

foo()
''', '''\
def foo(anObject) {

    print anObject + anObject
}

foo(5)
''')
  }

  void testIntroduceFromInjection() {
    doTest(IntroduceParameterRefactoring.REPLACE_FIELDS_WITH_GETTERS_NONE, false, false, null, false ,'''\
def foo() {
  def bar = "bar"
  println "$<selection>bar</selection>"
}
''', '''\
def foo(String anObject) {
  def bar = "bar"
  println "${anObject}"
}
''')
  }

  void testIntroduceFromStringByCaret() {
    doTest IntroduceParameterRefactoring.REPLACE_FIELDS_WITH_GETTERS_NONE, false, false, null, false, '''\
def test() {
    createFile();
}

def createFile() {
     new File("na<caret>me").createNewFile()
}
''', '''\
def test() {
    createFile("name");
}

def createFile(String anObject) {
     new File(anObject).createNewFile()
}
'''
  }
}
