// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.refactoring.changeSignature;

import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.refactoring.BaseRefactoringProcessor;
import com.intellij.refactoring.changeSignature.JavaThrownExceptionInfo;
import com.intellij.refactoring.changeSignature.ThrownExceptionInfo;
import junit.framework.TestCase;
import org.codehaus.groovy.runtime.DefaultGroovyMethods;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.util.TestUtils;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * @author Maxim.Medvedev
 */
public class ChangeSignatureTest extends ChangeSignatureTestCase {
  public void testOneNewParameter() {
    doTest(new SimpleInfo("p", -1, "\"5\"", null, CommonClassNames.JAVA_LANG_STRING));
  }

  public void testRemoveParameterMultiline() {
    doTest(new SimpleInfo(0), new SimpleInfo(2));
  }

  public void testMoveParametersMultiline() {
    doTest(new SimpleInfo(1), new SimpleInfo(0), new SimpleInfo(2));
  }

  public void testRemoveParameter() {
    doTest();
  }

  public void testInsertParameter() {
    doTest(new SimpleInfo(0), new SimpleInfo("p", -1, "5", "-3", PsiTypes.intType()), new SimpleInfo(1));
  }

  public void testInsertOptionalParameter() {
    doTest(new SimpleInfo(0), new SimpleInfo(1), new SimpleInfo("p", -1, "5", "-3", PsiTypes.intType()));
  }

  public void testNamedParametersRemove() {
    doTest(new SimpleInfo(1), new SimpleInfo(2));
  }

  public void testNamedParametersOrder1() {
    doTest(new SimpleInfo(0),
           new SimpleInfo(2));
  }

  /*public void testNamedParametersOrder2() throws Exception {
    doTest(new SimpleInfo[]{
      new SimpleInfo(0),
      new SimpleInfo("p", -1, "5", null, com.intellij.psi.PsiTypes.intType(),
      new SimpleInfo(2),
                                 });
  }

  public void testNamedParametersOrder3() throws Exception {
    doTest(new SimpleInfo[]{
      new SimpleInfo(0),
      new SimpleInfo(2),
      new SimpleInfo("p", -1, "5", null, com.intellij.psi.PsiTypes.intType(),
                                 });
  }*/

  public void testMoveNamedParameters() throws Exception {
    doTest(new SimpleInfo(1),
           new SimpleInfo(0));
  }

  public void testMoveVarArgParameters() {
    doTest(new SimpleInfo(1), new SimpleInfo(0));
  }

  public void testChangeVisibilityAndName() {
    doTest(PsiModifier.PROTECTED, "newName", null, new ArrayList<>(Arrays.asList(new SimpleInfo(0))),
           new ArrayList<>(), false);
  }

  public void testImplicitConstructorInConstructor() {
    doTest(new SimpleInfo("p", -1, "5", null, PsiTypes.intType()));
  }

  public void testImplicitConstructorForClass() {
    doTest(new SimpleInfo("p", -1, "5", null, PsiTypes.intType()));
  }

  public void testAnonymousClassUsage() {
    doTest(new SimpleInfo("p", -1, "5", null, PsiTypes.intType()));
  }

  public void testGroovyDocReferences() {
    doTest(new SimpleInfo(0), new SimpleInfo(2));
  }

  public void testOverriders() {
    doTest(PsiModifier.PUBLIC, "bar", null, new ArrayList<>(Arrays.asList(new SimpleInfo(0))),
           new ArrayList<>(), false);
  }

  public void testParameterRename() {
    doTest(new SimpleInfo("newP", 0));
  }

  public void testAddReturnType() {
    doTest("int", new SimpleInfo(0));
  }

  public void testChangeReturnType() {
    doTest("int", new SimpleInfo(0));
  }

  public void testRemoveReturnType() {
    doTest("", new SimpleInfo(0));
  }

  public void testChangeParameterType() {
    doTest("", new SimpleInfo("p", 0, null, null, PsiTypes.intType()));
  }

  public void testGenerateDelegate() {
    doTest("", true, new SimpleInfo(0), new SimpleInfo("p", -1, "2", "2", PsiTypes.intType()));
  }

  public void testAddException() {
    doTest(PsiModifier.PUBLIC, null, "",
           Arrays.asList(new SimpleInfo(0)),
           Arrays.asList(new JavaThrownExceptionInfo(-1, (PsiClassType)createType("java.io.IOException"))),
           false);
  }

  public void testExceptionCaughtInUsage() {
    doTest(PsiModifier.PUBLIC, null, "",
           Arrays.asList(new SimpleInfo(0)),
           Arrays.asList(new JavaThrownExceptionInfo(-1, (PsiClassType)createType("java.io.IOException"))),
           false);
  }

  public void testExceptionInClosableBlock() {
    doTest(PsiModifier.PUBLIC, null, "",
           Arrays.asList(new SimpleInfo(0)),
           Arrays.asList(new JavaThrownExceptionInfo(-1, (PsiClassType)createType("java.io.IOException"))),
           false);
  }

  public void testGenerateDelegateForConstructor() {
    doTest(PsiModifier.PUBLIC, "Foo", null,
           new ArrayList<>(Arrays.asList(new SimpleInfo(0), new SimpleInfo("a", -1, "5", null, PsiTypes.intType()))),
           new ArrayList<>(), true);
  }

  public void testGenerateDelegateForAbstract() {
    doTest(PsiModifier.PUBLIC, "foo", null,
           new ArrayList<>(Arrays.asList(new SimpleInfo(0), new SimpleInfo("a", -1, "5", null, PsiTypes.intType()))),
           new ArrayList<>(), true);
  }

  public void testTypeParameters() {
    doTest(new SimpleInfo("list", -1, "null", null, "java.util.List<T>"), new SimpleInfo(0));
  }

  public void testEnumConstructor() throws Exception {
    doTest(new SimpleInfo("a", -1, "2", null, PsiTypes.intType()));
  }

  public void testMoveArrayToTheEnd() {
    doTest(new SimpleInfo(1), new SimpleInfo(0));
  }

  public void testReplaceVarargWithArray() {
    doTest(new SimpleInfo("l", 1, null, null, "List<T>[]"), new SimpleInfo(0));
  }

  public void testReplaceVarargWithArray2() {
    doTest(new SimpleInfo("l", 1, null, null, "Map<T, E>[]"), new SimpleInfo(0));
  }

  public void testConstructorCall() {
    doTest(new SimpleInfo(0), new SimpleInfo("a", -1, "1", null, PsiTypes.intType()));
  }

  public void testNoArgInCommandCall() {
    doTest();
  }

  public void testClosureArgs() {
    doTest(new SimpleInfo(0));
  }

  public void testRemoveSingleClosureArgument() {
    doTest();
  }

  public void testNewExpr() {
    doTest();
  }

  public void testChangeJavaDoc() {
    doTest(new SimpleInfo("newName", 0), new SimpleInfo(1));
  }

  public void testDefaultInitializerInJava() {
    doTest(new SimpleInfo("p", -1, "", "1", ""));
  }

  public void testChangeType() {
    doTest(PsiModifier.PUBLIC, "foo", "List<String>", new ArrayList<>(), new ArrayList<>(), false);
  }

  public void testDifferentParamNameInOverriden() {
    doTest(new SimpleInfo("newName", 0));
  }

  public void testFeelLucky() {
    doTest(new SimpleInfo("lucky", -1, "defValue", "defInit", "java.lang.String", true));
  }

  public void testParamsWithGenerics() {
    doTest(new SimpleInfo(0));
  }

  public void testGenerateDelegateWithOtherName() {
    doTest(PsiModifier.PUBLIC, "doSmthElse", null, new ArrayList<>(), new ArrayList<>(), true);
  }

  public void testFailBecauseOfOptionalParam() {
    try {
      doTest(new SimpleInfo("optional", -1, null, "1", "int"));
    }
    catch (BaseRefactoringProcessor.ConflictsInTestsException e) {
      TestCase.assertEquals("Method <b><code>foo(int)</code></b> is already defined in class <b><code>Test</code></b>", e.getMessage());
      return;
    }

    TestCase.fail("conflicts are not detected!");
  }

  public void testRenameToLiteral() {
    doTest(null, "a bc", null, new ArrayList<>(), new ArrayList<>(), false);
  }

  public void testRenameToLiteral2() {
    doTest(null, "a'bc", null, new ArrayList<>(), new ArrayList<>(), false);
  }

  public void testLineFeedInCommandArgs() {
    doTest(new SimpleInfo(1));
  }

  public void testTraitMethod() {
    doTest(null, null, null, new ArrayList<>(Arrays.asList(new SimpleInfo("a", -1))), new ArrayList<>(),
           false);
  }

  private PsiType createType(String typeText) {
    return JavaPsiFacade.getElementFactory(getProject()).createTypeByFQClassName(typeText, GlobalSearchScope.allScope(getProject()));
  }

  private void doTest(String newReturnType, boolean generateDelegate, SimpleInfo... parameterInfos) {
    doTest(PsiModifier.PUBLIC, null, newReturnType, DefaultGroovyMethods.asType(parameterInfos, List.class),
           new ArrayList<>(), generateDelegate);
  }

  private void doTest(String newReturnType, SimpleInfo... parameterInfos) {
    doTest(newReturnType, false, parameterInfos);
  }

  private void doTest(SimpleInfo... parameterInfos) {
    doTest(null, false, parameterInfos);
  }

  private void doTest(@Nullable @PsiModifier.ModifierConstant String newVisibility,
                      @Nullable String newName,
                      @Nullable String newReturnType,
                      @NotNull List<SimpleInfo> parameterInfo,
                      @NotNull List<ThrownExceptionInfo> exceptionInfo,
                      final boolean generateDelegate) {
    final String javaTestName = getTestName(false) + ".java";
    final String groovyTestName = getTestName(false) + ".groovy";


    final File javaSrc = new File(getTestDataPath() + "/" + javaTestName);
    if (javaSrc.exists()) {
      myFixture.copyFileToProject(javaTestName);
    }


    myFixture.configureByFile(groovyTestName);
    executeRefactoring(newVisibility, newName, newReturnType, new SimpleParameterGen(parameterInfo, getProject()),
                       new SimpleExceptionsGen(exceptionInfo), generateDelegate);
    if (javaSrc.exists()) {
      myFixture.checkResultByFile(javaTestName, getTestName(false) + "_after.java", true);
    }

    myFixture.checkResultByFile(groovyTestName, getTestName(false) + "_after.groovy", true);
  }

  @Override
  public final String getBasePath() {
    return basePath;
  }

  private final String basePath = TestUtils.getTestDataPath() + "refactoring/changeSignature/";
}
