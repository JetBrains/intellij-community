// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.fixes;

import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.psi.impl.source.PostprocessReformattingAspect;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.plugins.groovy.lang.highlighting.GrHighlightingTestBase;
import org.jetbrains.plugins.groovy.util.TestUtils;
import org.junit.Assert;

import java.util.List;

public class CreateFieldFromJavaUsageTest extends GrHighlightingTestBase {
  private static final String BEFORE = "Before.groovy";
  private static final String AFTER = "After.groovy";
  private static final String JAVA = "Area.java";

  @Override
  public final String getBasePath() {
    return TestUtils.getTestDataPath() + "fixes/createFieldFromJava/" + getTestName(true) + "/";
  }

  @Override
  public void setUp() throws Exception {
    super.setUp();
    myFixture.configureByFiles(JAVA, BEFORE);
    myFixture.enableInspections(getCustomInspections());
  }

  private void doTest() {
    List<IntentionAction> fixes = myFixture.filterAvailableIntentions("Create field");
    Assert.assertFalse(fixes.isEmpty());
    myFixture.launchAction(ContainerUtil.getFirstItem(fixes));
    PostprocessReformattingAspect.getInstance(getProject()).doPostponedFormatting();
    myFixture.checkResultByFile(BEFORE, AFTER, true);
  }

  public void testArrayBraces() {
    doTest();
  }

  public void testSimple() {
    doTest();
  }

  public void testUppercaseField() {
    doTest();
  }

  public void testExpectedTypes() {
    doTest();
  }

  public void testFromEquals() {
    doTest();
  }

  public void testFromEqualsToPrimitiveType() {
    doTest();
  }

  public void testInnerGeneric() {
    doTest();
  }

  public void testInnerGenericArray() {
    doTest();
  }

  public void testMultipleTypes() {
    doTest();
  }

  public void testMultipleTypes2() {
    doTest();
  }

  public void testParametricMethod() {
    doTest();
  }

  public void testGroovyInheritor() {
    doTest();
  }

  public void testJavaInheritor() {
    doTest();
  }

  public void testTypeArgs() {
    doTest();
  }

  public void testScript() {
    doTest();
  }

  public void testSortByRelevance() {
    getFixture().addClass("""
    public class Foo {
      public void put(Object key, Object value) {
      }
    }
    """);
    doTest();
  }
}
