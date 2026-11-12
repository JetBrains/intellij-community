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
import static org.junit.jupiter.api.Assertions.assertFalse;

@TestApplication
public class GrCreateFieldFromJavaUsageTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture(GroovyProjectDescriptors.GROOVY_2_1);
  private final TestFixture<GrHighlightingFixture> highlighting = grHighlightingFixture(projectFixture);

  private static final String BEFORE = "Before.groovy";
  private static final String AFTER = "After.groovy";
  private static final String JAVA = "Area.java";

  @BeforeEach
  void setUp() {
    LightJavaCodeInsightTestFixture myFixture = highlighting.get().getFixture();
    highlighting.get().getFixture().setTestDataPath(TestUtils.getAbsoluteTestDataPath() + "fixes/createFieldFromJava/" + highlighting.get().getFixture().getTestName(true) + "/");
    myFixture.configureByFiles(JAVA, BEFORE);
    myFixture.enableInspections(highlighting.get().getCustomInspections());
  }

  private void doTest() {
    LightJavaCodeInsightTestFixture myFixture = highlighting.get().getFixture();
    List<IntentionAction> fixes = myFixture.filterAvailableIntentions("Create field");
    assertFalse(fixes.isEmpty());
    myFixture.launchAction(ContainerUtil.getFirstItem(fixes));
    PostprocessReformattingAspect.getInstance(myFixture.getProject()).doPostponedFormatting();
    myFixture.checkResultByFile(BEFORE, AFTER, true);
  }

  @Test
  public void testArrayBraces() {
    doTest();
  }

  @Test
  public void testSimple() {
    doTest();
  }

  @Test
  public void testUppercaseField() {
    doTest();
  }

  @Test
  public void testExpectedTypes() {
    doTest();
  }

  @Test
  public void testFromEquals() {
    doTest();
  }

  @Test
  public void testFromEqualsToPrimitiveType() {
    doTest();
  }

  @Test
  public void testInnerGeneric() {
    doTest();
  }

  @Test
  public void testInnerGenericArray() {
    doTest();
  }

  @Test
  public void testMultipleTypes() {
    doTest();
  }

  @Test
  public void testMultipleTypes2() {
    doTest();
  }

  @Test
  public void testParametricMethod() {
    doTest();
  }

  @Test
  public void testGroovyInheritor() {
    doTest();
  }

  @Test
  public void testJavaInheritor() {
    doTest();
  }

  @Test
  public void testTypeArgs() {
    doTest();
  }

  @Test
  public void testScript() {
    doTest();
  }

  @Test
  public void testSortByRelevance() {
    LightJavaCodeInsightTestFixture myFixture = highlighting.get().getFixture();
    myFixture.addClass("""
    public class Foo {
      public void put(Object key, Object value) {
      }
    }
    """);
    doTest();
  }
}
