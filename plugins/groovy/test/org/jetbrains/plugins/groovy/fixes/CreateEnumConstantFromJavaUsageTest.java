// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.fixes;

import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.psi.impl.source.PostprocessReformattingAspect;
import org.codehaus.groovy.runtime.DefaultGroovyMethods;
import org.jetbrains.plugins.groovy.lang.highlighting.GrHighlightingTestBase;
import org.jetbrains.plugins.groovy.util.TestUtils;

import java.util.List;

public class CreateEnumConstantFromJavaUsageTest extends GrHighlightingTestBase {
  private static final String BEFORE = "Before.groovy";
  private static final String AFTER = "After.groovy";
  private static final String JAVA = "Area.java";

  @Override
  public final String getBasePath() {
    return TestUtils.getTestDataPath() + "fixes/createEnumConstantFromUsage/" + getTestName(true) + "/";
  }

  @Override
  public void setUp() throws Exception {
    super.setUp();
    getFixture().configureByFiles(JAVA, BEFORE);
    getFixture().enableInspections(getCustomInspections());
  }

  private void doTest() {
    List<IntentionAction> fixes = myFixture.filterAvailableIntentions("Create enum constant");
    assert fixes.size() == 1;
    myFixture.launchAction(DefaultGroovyMethods.first(fixes));
    PostprocessReformattingAspect.getInstance(getProject()).doPostponedFormatting();
    myFixture.checkResultByFile(BEFORE, AFTER, true);
  }

  public void testSimple() {
    doTest();
  }

  public void testSimple2() {
    doTest();
  }

  public void testEmptyEnum() {
    doTest();
  }

  public void testEmptyEnumWithMethods() {
    doTest();
  }

  public void testWithConstructorArguments() {
    doTest();
  }

  public void testWithStaticImport() {
    doTest();
  }

  public void testWithSwitch() {
    doTest();
  }

  public void testWithSwitch2() {
    doTest();
  }

  public void testWithVarargs() {
    doTest();
  }
}
