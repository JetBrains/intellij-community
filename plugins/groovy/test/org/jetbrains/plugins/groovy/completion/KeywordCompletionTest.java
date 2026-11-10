// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.completion;

import com.intellij.codeInsight.CodeInsightSettings;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiMember;
import com.intellij.psi.PsiPackage;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.jetbrains.plugins.groovy.lang.psi.api.GroovyResolveResult;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariable;
import org.jetbrains.plugins.groovy.util.TestUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Iterator;
import java.util.Set;
import java.util.TreeSet;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaCodeInsightFixture;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;

@TestApplication
public class KeywordCompletionTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture();
  private final TestFixture<LightJavaCodeInsightTestFixture> codeInsightFixture = lightJavaCodeInsightFixture(projectFixture);

  private boolean oldAutoInsert;

  @Test
  public void testBr1() { doTest(); }
  @Test
  public void testCase_return() { doTest(); }
  @Test
  public void testClass1() { doTest(); }
  @Test
  public void testClass2() { doTest(); }
  @Test
  public void testClass3() { doTest(); }
  @Test
  public void testClass4() { doTest(); }
  @Test
  public void testExpr1() { doTest(); }
  @Test
  public void testExpr2() { doTest(); }
  @Test
  public void testFile11() { doTest(); }
  @Test
  public void testFile12() { doTest(); }
  @Test
  public void testFin() { doTest(); }
  @Test
  public void testFin2() { doTest(); }
  @Test
  public void testGRVY1064() { doTest(); }
  @Test
  public void testGrvy1404() { doTest(); }
  @Test
  public void testImp1() { doTest(); }
  @Test
  public void testImp2() { doTest(); }
  @Test
  public void testImp3() { doTest(); }
  @Test
  public void testIns1() { doTest(); }
  @Test
  public void testIns2() { doTest(); }
  @Test
  public void testIns3() { doTest(); }
  @Test
  public void testInt1() { doTest(); }
  @Test
  public void testLocal1() { doTest(); }
  @Test
  public void testMod1() { doTest(); }
  @Test
  public void testMod10() { doTest(); }
  @Test
  public void testMod11() { doTest(); }
  @Test
  public void testMod2() { doTest(); }
  @Test
  public void testMod3() { doTest(); }
  @Test
  public void testMod4() { doTest(); }
  @Test
  public void testMod5() { doTest(); }
  @Test
  public void testMod6() { doTest(); }
  @Test
  public void testMod7() { doTest(); }
  @Test
  public void testMod8() { doTest(); }
  @Test
  public void testMod9() { doTest(); }
  @Test
  public void testPack1() { doTest(); }
  @Test
  public void testSt1() { doTest(); }
  @Test
  public void testSwit1() { doTest(); }
  @Test
  public void testSwit13() { doTest(); }
  @Test
  public void testSwit14() { doTest(); }
  @Test
  public void testSwit2() { doTest(); }
  @Test
  public void testSwit3() { doTest(); }
  @Test
  public void testSwit4() { doTest(); }
  @Test
  public void testSwit5() { doTest(); }
  @Test
  public void testTag1() { doTest(); }
  @Test
  public void testTag2() { doTest(); }
  @Test
  public void testTag3() { doTest(); }
  @Test
  public void testTag4() { doTest(); }
  @Test
  public void testTh1() { doTest(); }
  @Test
  public void testTh2() { doTest(); }
  @Test
  public void testVar1() { doTest(); }
  @Test
  public void testVar10() { doTest(); }
  @Test
  public void testVar13() { doTest(); }
  @Test
  public void testVar2() { doTest(); }
  @Test
  public void testVar3() { doTest(); }
  @Test
  public void testVar4() { doTest(); }
  @Test
  public void testVar5() { doTest(); }
  @Test
  public void testVar6() { doTest(); }
  @Test
  public void testVar7() { doTest(); }
  @Test
  public void testVar8() { doTest(); }
  @Test
  public void testWhile55() { doTest(); }
  @Test
  public void testDefInsideCase() { doTest(); }
  @Test
  public void testThrows1() { doTest(); }
  @Test
  public void testThrows2() { doTest(); }
  @Test
  public void testThrows3() { doTest(); }
  @Test
  public void testPrimitiveTypes() { doTest(); }
  @Test
  public void testIncompleteConstructor() { doTest(); }
  @Test
  public void testAtInterface() { doTest(); }
  @Test
  public void testInstanceOf() { doTest(); }
  @Test
  public void testAssert() { doTest(); }
  @Test
  public void testReturn() { doTest(); }
  @Test
  public void testAssertInClosure() { doTest(); }
  @Test
  public void testAfterLabel() { doTest(); }
  @Test
  public void testKeywordsInParentheses() { doTest(); }
  @Test
  public void testCompletionInTupleVar() { doTest(); }
  @Test
  public void testAnnotationArg() { doTest(); }
  @Test
  public void testDefaultAnnotationArg() { doTest(); }
  @Test
  public void testDefaultInAnnotation() { doTest(); }
  @Test
  public void testElse1() { doTest(); }
  @Test
  public void testElse2() { doTest(); }
  @Test
  public void testElse3() { doTest(); }
  @Test
  public void testClassAfterAnnotation() { doTest(); }
  @Test
  public void testClassAfterAnno2() { doTest(); }
  @Test
  public void testExtends() { doTest(); }
  @Test
  public void testImplements() { doTest(); }
  @Test
  public void testAfterNumberLiteral() { doTest(); }
  @Test
  public void testDoWhile() { doTest(); }
  @Test
  public void testDoWhile2() { doTest(); }
  @Test
  public void testNoVarInFrontOfMethod() { doTest(); }
  @Test
  public void testNoVarInFrontOfMethod2() { doTest(); }
  @Test
  public void testImportAlias() { doTest(); }

  @BeforeEach
  void setUp() {
    codeInsightFixture.get().setTestDataPath(TestUtils.getAbsoluteTestDataPath() + "groovy/oldCompletion/keyword");
    CodeInsightSettings instance = CodeInsightSettings.getInstance();
    oldAutoInsert = instance.AUTOCOMPLETE_ON_CODE_COMPLETION;
    instance.AUTOCOMPLETE_ON_CODE_COMPLETION = false;
  }

  @AfterEach
  void tearDown() {
    CodeInsightSettings.getInstance().AUTOCOMPLETE_ON_CODE_COMPLETION = oldAutoInsert;
  }

  protected void doTest() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    final String testName = myFixture.getTestName(true);
    String fileName = testName + ".test";
    final Iterator<String> iterator = TestUtils.readInput(myFixture.getTestDataPath() + "/" + fileName).iterator();
    final String input = iterator.hasNext() ? iterator.next() : null;

    myFixture.configureByText(testName + ".groovy", input);
    Set<String> result = new TreeSet<>();
    for (LookupElement element : myFixture.completeBasic()) {
      Object object = element.getObject();
      if (!(object instanceof PsiMember) && !(object instanceof GrVariable) && !(object instanceof GroovyResolveResult) && !(object instanceof PsiPackage)) {
        result.add(element.getLookupString());
      }
    }
    String actual = StringUtil.join(result, "\n");
    myFixture.configureByText("actual.txt", input + "\n-----\n" + actual);
    myFixture.checkResultByFile(fileName);
  }
}
