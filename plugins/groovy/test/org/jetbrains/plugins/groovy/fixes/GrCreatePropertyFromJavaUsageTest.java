// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.fixes;

import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.psi.impl.source.PostprocessReformattingAspect;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.plugins.groovy.GroovyProjectDescriptors;
import org.jetbrains.plugins.groovy.lang.highlighting.GrHighlightingFixture;
import org.jetbrains.plugins.groovy.util.TestUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static org.jetbrains.plugins.groovy.lang.highlighting.GrHighlightingFixtureKt.grHighlightingFixture;
import static org.junit.jupiter.api.Assertions.assertEquals;

@TestApplication
public class GrCreatePropertyFromJavaUsageTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture(GroovyProjectDescriptors.GROOVY_2_1);
  private final TestFixture<GrHighlightingFixture> highlighting = grHighlightingFixture(projectFixture);

  private static final String BEFORE = "Before.groovy";
  private static final String AFTER = "After.groovy";
  private static final String JAVA = "Area.java";
  private static final String CREATE_PROPERTY = "Create property";
  private static final String CREATE_RO_PROPERTY = "Create read-only property";

  @BeforeEach
  void setUp() {
    LightJavaCodeInsightTestFixture myFixture = highlighting.get().getFixture();
    highlighting.get().getFixture().setTestDataPath(TestUtils.getAbsoluteTestDataPath() + "fixes/createPropertyFromJava/" + highlighting.get().getFixture().getTestName(true) + "/");
    myFixture.configureByFiles(JAVA, BEFORE);
    myFixture.enableInspections(highlighting.get().getCustomInspections());
  }

  private void doTest(String action, int actionCount) {
    LightJavaCodeInsightTestFixture myFixture = highlighting.get().getFixture();
    List<IntentionAction> fixes = myFixture.filterAvailableIntentions(action);
    assertEquals(fixes.size(), actionCount);
    if (actionCount == 0) return;

    myFixture.launchAction(ContainerUtil.getFirstItem(fixes));
    PostprocessReformattingAspect.getInstance(myFixture.getProject()).doPostponedFormatting();
    myFixture.checkResultByFile(BEFORE, AFTER, true);
  }

  private void doTest(String action) {
    doTest(action, 1);
  }

  private void doTest() {
    doTest(CREATE_PROPERTY, 1);
  }

  @Test
  public void testFromSetter() {
    doTest();
  }

  @Test
  public void testFromGetter() {
    doTest();
  }

  @Test
  public void testFromGetterWithFirstLowerLetter() {
    doTest();
  }

  @Test
  public void testBooleanFromGetter() {
    doTest();
  }

  @Test
  public void testFromGetterReadOnly() {
    doTest(CREATE_RO_PROPERTY);
  }

  @Test
  public void testBooleanFromGetterReadOnly() {
    doTest(CREATE_RO_PROPERTY);
  }

  @Test
  public void testFromSetterReadOnly() {
    doTest(CREATE_RO_PROPERTY, 0);
  }

  @Test
  public void testPassedLambda() {
    doTest();
  }

  @Test
  public void testFromSetterWithExistGetter() {
    doTest(CREATE_PROPERTY, 0);
  }

  @Test
  public void testFromSetterWithFieldExist() {
    doTest(CREATE_PROPERTY, 0);
  }

  @Test
  public void testFromBooleanGetterWithExistSetter() {
    doTest(CREATE_PROPERTY, 0);
  }

  @Test
  public void testFromBooleanGetterWithArgs() {
    doTest(CREATE_PROPERTY, 0);
  }

  @Test
  public void testFromSetterWithoutArgs() {
    doTest(CREATE_PROPERTY, 0);
  }

  @Test
  public void testInInterface() {
    doTest(CREATE_PROPERTY, 0);
  }

  @Test
  public void testFromGetterWithLowercaseName() {
    doTest(CREATE_PROPERTY, 0);
  }
}
