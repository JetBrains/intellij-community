// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.fixes;

import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.psi.impl.source.PostprocessReformattingAspect;
import org.codehaus.groovy.runtime.DefaultGroovyMethods;
import org.jetbrains.plugins.groovy.lang.highlighting.GrHighlightingTestBase;
import org.jetbrains.plugins.groovy.util.TestUtils;

import java.util.List;

public class CreatePropertyFromJavaUsageTest extends GrHighlightingTestBase {
  private static final String BEFORE = "Before.groovy";
  private static final String AFTER = "After.groovy";
  private static final String JAVA = "Area.java";
  private static final String CREATE_PROPERTY = "Create property";
  private static final String CREATE_RO_PROPERTY = "Create read-only property";

  @Override
  public final String getBasePath() {
    return TestUtils.getTestDataPath() + "fixes/createPropertyFromJava/" + getTestName(true) + "/";
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
    doTest(CREATE_PROPERTY, 1);
  }

  public void testFromSetter() {
    doTest();
  }

  public void testFromGetter() {
    doTest();
  }

  public void testFromGetterWithFirstLowerLetter() {
    doTest();
  }

  public void testBooleanFromGetter() {
    doTest();
  }

  public void testFromGetterReadOnly() {
    doTest(CREATE_RO_PROPERTY);
  }

  public void testBooleanFromGetterReadOnly() {
    doTest(CREATE_RO_PROPERTY);
  }

  public void testFromSetterReadOnly() {
    doTest(CREATE_RO_PROPERTY, 0);
  }

  public void testPassedLambda() {
    doTest();
  }

  public void testFromSetterWithExistGetter() {
    doTest(CREATE_PROPERTY, 0);
  }

  public void testFromSetterWithFieldExist() {
    doTest(CREATE_PROPERTY, 0);
  }

  public void testFromBooleanGetterWithExistSetter() {
    doTest(CREATE_PROPERTY, 0);
  }

  public void testFromBooleanGetterWithArgs() {
    doTest(CREATE_PROPERTY, 0);
  }

  public void testFromSetterWithoutArgs() {
    doTest(CREATE_PROPERTY, 0);
  }

  public void testInInterface() {
    doTest(CREATE_PROPERTY, 0);
  }

  public void testFromGetterWithLowercaseName() {
    doTest(CREATE_PROPERTY, 0);
  }
}
