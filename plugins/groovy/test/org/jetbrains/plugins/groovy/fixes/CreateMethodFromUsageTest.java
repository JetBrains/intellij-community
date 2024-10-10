// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.fixes;

import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.psi.impl.source.PostprocessReformattingAspect;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.plugins.groovy.codeInspection.assignment.GroovyAssignabilityCheckInspection;
import org.jetbrains.plugins.groovy.codeInspection.untypedUnresolvedAccess.GrUnresolvedAccessInspection;
import org.jetbrains.plugins.groovy.lang.highlighting.GrHighlightingTestBase;
import org.jetbrains.plugins.groovy.util.TestUtils;
import org.junit.Assert;

import java.util.List;

public class CreateMethodFromUsageTest extends GrHighlightingTestBase {
  @Override
  public final String getBasePath() {
    return TestUtils.getTestDataPath() + "fixes/createMethodFromUsage/" + getTestName(true) + "/";
  }

  @Override
  public void setUp() throws Exception {
    super.setUp();
    getFixture().enableInspections(GrUnresolvedAccessInspection.class, GroovyAssignabilityCheckInspection.class);
  }

  private void doTest(String action, int actionCount, String[] files) {
    myFixture.configureByFiles(files);
    List<IntentionAction> fixes = myFixture.filterAvailableIntentions(action);
    Assert.assertEquals(fixes.size(), actionCount);
    if (actionCount == 0) return;

    myFixture.launchAction(ContainerUtil.getFirstItem(fixes));
    PostprocessReformattingAspect.getInstance(getProject()).doPostponedFormatting();
    myFixture.checkResultByFile(BEFORE, AFTER, true);
  }

  private void doTest(String action, int actionCount) {
    doTest(action, actionCount, new String[]{USAGE, BEFORE});
  }

  private void doTest(String action) {
    doTest(action, 1);
  }

  private void doTest() {
    doTest(CREATE_METHOD);
  }

  public void testSimple1() {
    doTest();
  }

  public void _testSimple2() {
    doTest();
  }

  public void _testSimple3() {
    doTest();
  }

  public void testSimple4() {
    doTest();
  }

  public void testApplicationStatement() {
    doTest(CREATE_METHOD, 1, new String[]{BEFORE});
  }

  public void testInapplicableApplicationStatement() {
    doTest(CREATE_METHOD, 1, new String[]{BEFORE});
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

  public void testClosureArgument() {
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

  public void _testParameterNameSuggestion() {
    doTest();
  }

  public void _testPolyadicExpression() {
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
    doTest(CREATE_METHOD, 1);
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

  public void testConstructorInvocation() {
    doTest(CREATE_CONSTRUCTOR, 1, new String[]{BEFORE});
  }

  public void testSuperConstructorInvocation() {
    doTest(CREATE_CONSTRUCTOR, 1, new String[]{BEFORE});
  }

  public void testSameParameterNames1() {
    doTest();
  }

  public void testSameParameterNames2() {
    doTest();
  }

  public void testSameParameterNames3() {
    doTest();
  }

  private static final String BEFORE = "Before.groovy";
  private static final String AFTER = "After.groovy";
  private static final String USAGE = "script.groovy";
  private static final String CREATE_METHOD = "Create method";
  private static final String CREATE_ABSTRACT_METHOD = "Create abstract method";
  private static final String CREATE_CONSTRUCTOR = "Create constructor";
}
