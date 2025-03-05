// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.completion;

import com.intellij.codeInsight.CodeInsightSettings;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiMember;
import com.intellij.psi.PsiPackage;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import org.jetbrains.plugins.groovy.lang.psi.api.GroovyResolveResult;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariable;
import org.jetbrains.plugins.groovy.util.TestUtils;

import java.util.Iterator;
import java.util.Set;
import java.util.TreeSet;

public class KeywordCompletionTest extends LightJavaCodeInsightFixtureTestCase {
  public void testBr1() { doTest(); }

  public void testCase_return() { doTest(); }

  public void testClass1() { doTest(); }

  public void testClass2() { doTest(); }

  public void testClass3() { doTest(); }

  public void testClass4() { doTest(); }

  public void testExpr1() { doTest(); }

  public void testExpr2() { doTest(); }

  public void testFile11() { doTest(); }

  public void testFile12() { doTest(); }

  public void testFin() { doTest(); }

  public void testFin2() { doTest(); }

  public void testGRVY1064() { doTest(); }

  public void testGrvy1404() { doTest(); }

  public void testImp1() { doTest(); }

  public void testImp2() { doTest(); }

  public void testImp3() { doTest(); }

  public void testIns1() { doTest(); }

  public void testIns2() { doTest(); }

  public void testIns3() { doTest(); }

  public void testInt1() { doTest(); }

  public void testLocal1() { doTest(); }

  public void testMod1() { doTest(); }

  public void testMod10() { doTest(); }

  public void testMod11() { doTest(); }

  public void testMod2() { doTest(); }

  public void testMod3() { doTest(); }

  public void testMod4() { doTest(); }

  public void testMod5() { doTest(); }

  public void testMod6() { doTest(); }

  public void testMod7() { doTest(); }

  public void testMod8() { doTest(); }

  public void testMod9() { doTest(); }

  public void testPack1() { doTest(); }

  public void testSt1() { doTest(); }

  public void testSwit1() { doTest(); }

  public void testSwit13() { doTest(); }

  public void testSwit14() { doTest(); }

  public void testSwit2() { doTest(); }

  public void testSwit3() { doTest(); }

  public void testSwit4() { doTest(); }

  public void testSwit5() { doTest(); }

  public void testTag1() { doTest(); }

  public void testTag2() { doTest(); }

  public void testTag3() { doTest(); }

  public void testTag4() { doTest(); }

  public void testTh1() { doTest(); }

  public void testTh2() { doTest(); }

  public void testVar1() { doTest(); }

  public void testVar10() { doTest(); }

  public void testVar13() { doTest(); }

  public void testVar2() { doTest(); }

  public void testVar3() { doTest(); }

  public void testVar4() { doTest(); }

  public void testVar5() { doTest(); }

  public void testVar6() { doTest(); }

  public void testVar7() { doTest(); }

  public void testVar8() { doTest(); }

  public void testWhile55() { doTest(); }

  public void testDefInsideCase() { doTest(); }

  public void testThrows1() { doTest(); }

  public void testThrows2() { doTest(); }

  public void testThrows3() { doTest(); }

  public void testPrimitiveTypes() { doTest(); }

  public void testIncompleteConstructor() { doTest(); }

  public void testAtInterface() { doTest(); }

  public void testInstanceOf() { doTest(); }

  public void testAssert() { doTest(); }

  public void testReturn() { doTest(); }

  public void testAssertInClosure() { doTest(); }

  public void testAfterLabel() { doTest(); }

  public void testKeywordsInParentheses() { doTest(); }

  public void testCompletionInTupleVar() { doTest(); }

  public void testAnnotationArg() { doTest(); }

  public void testDefaultAnnotationArg() { doTest(); }

  public void testDefaultInAnnotation() { doTest(); }

  public void testElse1() { doTest(); }

  public void testElse2() { doTest(); }

  public void testElse3() { doTest(); }

  public void testClassAfterAnnotation() { doTest(); }

  public void testClassAfterAnno2() { doTest(); }

  public void testExtends() { doTest(); }

  public void testImplements() { doTest(); }

  public void testAfterNumberLiteral() { doTest(); }

  public void testDoWhile() { doTest(); }

  public void testDoWhile2() { doTest(); }

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    CodeInsightSettings instance = CodeInsightSettings.getInstance();
    oldAutoInsert = instance.AUTOCOMPLETE_ON_CODE_COMPLETION;
    instance.AUTOCOMPLETE_ON_CODE_COMPLETION = false;
  }

  @Override
  protected void tearDown() throws Exception {
    try {
      CodeInsightSettings.getInstance().AUTOCOMPLETE_ON_CODE_COMPLETION = oldAutoInsert;
    }
    catch (Throwable e) {
      addSuppressedException(e);
    }
    finally {
      super.tearDown();
    }
  }

  protected void doTest() {
    final String testName = getTestName(true);
    String fileName = testName + ".test";
    final Iterator<String> iterator = TestUtils.readInput(getTestDataPath() + "/" + fileName).iterator();
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

  @Override
  public String getBasePath() {
    return basePath;
  }

  public void setBasePath(String basePath) {
    this.basePath = basePath;
  }

  private String basePath = TestUtils.getTestDataPath() + "groovy/oldCompletion/keyword";
  private boolean oldAutoInsert;
}
