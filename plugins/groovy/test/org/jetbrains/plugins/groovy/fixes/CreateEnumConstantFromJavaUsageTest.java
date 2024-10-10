// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.fixes;

import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.psi.impl.source.PostprocessReformattingAspect;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.plugins.groovy.lang.highlighting.GrHighlightingTestBase;
import org.jetbrains.plugins.groovy.util.TestUtils;
import org.junit.Assert;

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
    Assert.assertEquals(1, fixes.size());
    myFixture.launchAction(ContainerUtil.getFirstItem(fixes));
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
