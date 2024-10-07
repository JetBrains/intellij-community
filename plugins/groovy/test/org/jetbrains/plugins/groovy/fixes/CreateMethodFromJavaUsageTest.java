// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.fixes;

import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.psi.impl.source.PostprocessReformattingAspect;
import org.codehaus.groovy.runtime.DefaultGroovyMethods;
import org.jetbrains.plugins.groovy.lang.highlighting.GrHighlightingTestBase;
import org.jetbrains.plugins.groovy.util.TestUtils;

import java.util.List;

public class CreateMethodFromJavaUsageTest extends GrHighlightingTestBase {
  @Override
  public final String getBasePath() {
    return TestUtils.getTestDataPath() + "fixes/createMethodFromJava/" + getTestName(true) + "/";
  }

  @Override
  public void setUp() throws Exception {
    super.setUp();
    getFixture().configureByFiles(JAVA, BEFORE);
    getFixture().enableInspections(getCustomInspections());
  }

  private void doTest(String action, int actionCount) {

    List<IntentionAction> fixes = myFixture.filterAvailableIntentions(action);
    assert fixes.size() == actionCount;
    if (actionCount == 0) return;

    myFixture.launchAction(DefaultGroovyMethods.first(fixes));
    PostprocessReformattingAspect.getInstance(getProject()).doPostponedFormatting();
    myFixture.checkResultByFile(BEFORE, AFTER, true);
  }

  private void doTest(String action) {
    doTest(action, 1);
  }

  private void doTest() {
    doTest(CREATE_METHOD, 1);
  }

  public void testSimple1() {
    doTest();
  }

  public void testSimple2() {
    doTest();
  }

  public void testSimple3() {
    doTest();
  }

  public void testSimple4() {
    doTest();
  }

  public void testAbstract() {
    doTest(CREATE_ABSTRACT_METHOD);
  }

  public void testAbstractStatic() {
    doTest(CREATE_ABSTRACT_METHOD, 0);
  }

  public void testAbstractInNonAbstract() {
    doTest(CREATE_ABSTRACT_METHOD, 0);
  }

  public void testAbstractInInterface() {
    doTest();
  }

  public void testArrayParam() {
    doTest();
  }

  public void testAssertDescription() {
    doTest();
  }

  public void testGeneric() {
    doTest();
  }

  public void testMultiMap() {
    doTest();
  }

  public void testLambda() {
    doTest();
  }

  public void testMethodReference() {
    doTest();
  }

  public void testSeveralReturnTypes() {
    doTest();
  }

  public void testCapturedWildcard() {
    doTest();
  }

  public void testParameterNameSuggestion() {
    doTest();
  }

  public void testPolyadicExpression() {
    doTest();
  }

  public void testNestedExpression() {
    doTest();
  }

  public void testInAnonymousClass() {
    doTest();
  }

  public void testTypeParameterFromWildcard() {
    doTest();
  }

  public void testUnresolvedArg() {
    doTest(CREATE_METHOD, 0);
  }

  public void testIntegerCast() {
    doTest();
  }

  public void testSeveralArguments() {
    doTest();
  }

  public void testConstructor1() {
    doTest(CREATE_CONSTRUCTOR);
  }

  public void testConstructor2() {
    doTest(CREATE_CONSTRUCTOR);
  }

  public void testConstructorAnon() {
    doTest(CREATE_CONSTRUCTOR);
  }

  public void testConstructorInterface() {
    doTest(CREATE_CONSTRUCTOR, 0);
  }

  public void testConstructorTrait() {
    doTest(CREATE_CONSTRUCTOR, 0);
  }

  public void testConstructorEnum() {
    doTest(CREATE_CONSTRUCTOR, 0);
  }

  private static final String BEFORE = "Before.groovy";
  private static final String AFTER = "After.groovy";
  private static final String JAVA = "Area.java";
  private static final String CREATE_METHOD = "Create method";
  private static final String CREATE_ABSTRACT_METHOD = "Create abstract method";
  private static final String CREATE_CONSTRUCTOR = "Create constructor";
}
