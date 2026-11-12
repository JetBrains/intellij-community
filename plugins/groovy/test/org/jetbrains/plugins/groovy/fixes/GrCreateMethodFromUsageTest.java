// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.fixes;

import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.psi.impl.source.PostprocessReformattingAspect;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.plugins.groovy.GroovyProjectDescriptors;
import org.jetbrains.plugins.groovy.codeInspection.assignment.GroovyAssignabilityCheckInspection;
import org.jetbrains.plugins.groovy.codeInspection.untypedUnresolvedAccess.GrUnresolvedAccessInspection;
import org.jetbrains.plugins.groovy.lang.highlighting.GrHighlightingFixture;
import org.jetbrains.plugins.groovy.util.TestUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static org.jetbrains.plugins.groovy.lang.highlighting.GrHighlightingFixtureKt.grHighlightingFixture;
import static org.junit.jupiter.api.Assertions.assertEquals;

@TestApplication
public class GrCreateMethodFromUsageTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture(GroovyProjectDescriptors.GROOVY_2_1);
  private final TestFixture<GrHighlightingFixture> highlighting = grHighlightingFixture(projectFixture);

  @BeforeEach
  void setUp() {
    LightJavaCodeInsightTestFixture myFixture = highlighting.get().getFixture();
    highlighting.get().getFixture().setTestDataPath(TestUtils.getAbsoluteTestDataPath() + "fixes/createMethodFromUsage/" + highlighting.get().getFixture().getTestName(true) + "/");
    myFixture.enableInspections(GrUnresolvedAccessInspection.class, GroovyAssignabilityCheckInspection.class);
  }

  private void doTest(String action, int actionCount, String[] files) {
    LightJavaCodeInsightTestFixture myFixture = highlighting.get().getFixture();
    myFixture.configureByFiles(files);
    List<IntentionAction> fixes = myFixture.filterAvailableIntentions(action);
    assertEquals(fixes.size(), actionCount);
    if (actionCount == 0) return;

    myFixture.launchAction(ContainerUtil.getFirstItem(fixes));
    PostprocessReformattingAspect.getInstance(myFixture.getProject()).doPostponedFormatting();
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

  @Test
  public void testSimple1() {
    doTest();
  }

  public void _testSimple2() {
    doTest();
  }

  public void _testSimple3() {
    doTest();
  }

  @Test
  public void testSimple4() {
    doTest();
  }

  @Test
  public void testApplicationStatement() {
    doTest(CREATE_METHOD, 1, new String[]{BEFORE});
  }

  @Test
  public void testInapplicableApplicationStatement() {
    doTest(CREATE_METHOD, 1, new String[]{BEFORE});
  }

  @Test
  public void testAbstract() {
    doTest(CREATE_ABSTRACT_METHOD);
  }

  @Test
  public void testAbstractStatic() {
    doTest(CREATE_ABSTRACT_METHOD, 0);
  }

  @Test
  public void testAbstractInNonAbstract() {
    doTest(CREATE_ABSTRACT_METHOD, 0);
  }

  @Test
  public void testAbstractInInterface() {
    doTest();
  }

  @Test
  public void testArrayParam() {
    doTest();
  }

  @Test
  public void testAssertDescription() {
    doTest();
  }

  @Test
  public void testGeneric() {
    doTest();
  }

  @Test
  public void testMultiMap() {
    doTest();
  }

  @Test
  public void testClosureArgument() {
    doTest();
  }

  @Test
  public void testMethodReference() {
    doTest();
  }

  @Test
  public void testSeveralReturnTypes() {
    doTest();
  }

  @Test
  public void testCapturedWildcard() {
    doTest();
  }

  public void _testParameterNameSuggestion() {
    doTest();
  }

  public void _testPolyadicExpression() {
    doTest();
  }

  @Test
  public void testNestedExpression() {
    doTest();
  }

  @Test
  public void testInAnonymousClass() {
    doTest();
  }

  @Test
  public void testTypeParameterFromWildcard() {
    doTest();
  }

  @Test
  public void testUnresolvedArg() {
    doTest(CREATE_METHOD, 1);
  }

  @Test
  public void testIntegerCast() {
    doTest();
  }

  @Test
  public void testSeveralArguments() {
    doTest();
  }

  @Test
  public void testConstructor1() {
    doTest(CREATE_CONSTRUCTOR);
  }

  @Test
  public void testConstructor2() {
    doTest(CREATE_CONSTRUCTOR);
  }

  @Test
  public void testConstructorAnon() {
    doTest(CREATE_CONSTRUCTOR);
  }

  @Test
  public void testConstructorInterface() {
    doTest(CREATE_CONSTRUCTOR, 0);
  }

  @Test
  public void testConstructorTrait() {
    doTest(CREATE_CONSTRUCTOR, 0);
  }

  @Test
  public void testConstructorEnum() {
    doTest(CREATE_CONSTRUCTOR, 0);
  }

  @Test
  public void testConstructorInvocation() {
    doTest(CREATE_CONSTRUCTOR, 1, new String[]{BEFORE});
  }

  @Test
  public void testSuperConstructorInvocation() {
    doTest(CREATE_CONSTRUCTOR, 1, new String[]{BEFORE});
  }

  @Test
  public void testSameParameterNames1() {
    doTest();
  }

  @Test
  public void testSameParameterNames2() {
    doTest();
  }

  @Test
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
