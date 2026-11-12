// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.fixes;

import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.plugins.groovy.GroovyProjectDescriptors;
import org.jetbrains.plugins.groovy.lang.highlighting.GrHighlightingFixture;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static org.jetbrains.plugins.groovy.lang.highlighting.GrHighlightingFixtureKt.grHighlightingFixture;
import static org.junit.jupiter.api.Assertions.assertEquals;

@TestApplication
public class GrSpreadArgumentTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture(GroovyProjectDescriptors.GROOVY_LATEST);
  private final TestFixture<GrHighlightingFixture> highlighting = grHighlightingFixture(projectFixture);

  @Test
  public void testThrowingOutListLiteral() {
    doTest("""
             
             import groovy.transform.CompileStatic
             
             @CompileStatic
             class A {
                 def m(int a, String b) {
             
                 }
                 def m2() {
                     m(*[1,""])
                 }
             }
             """, """
             
             import groovy.transform.CompileStatic
             
             @CompileStatic
             class A {
                 def m(int a, String b) {
             
                 }
                 def m2() {
                     m(1, "")
                 }
             }
             """);
  }

  @Test
  public void testReplaceWithIndexAccess() {
    doTest("""
             
             import groovy.transform.CompileStatic
             
             @CompileStatic
             class A {
                 def m(int a, int b) {
                 }
                 def m2(List<Integer> a) {
                     m(*a)
                 }
             }
             """, """
             
             import groovy.transform.CompileStatic
             
             @CompileStatic
             class A {
                 def m(int a, int b) {
                 }
                 def m2(List<Integer> a) {
                     m(a[0], a[1])
                 }
             }
             """);
  }

  @Test
  public void testReplaceWithIndexAccessOnExtractedVariable() {
    doTest("""
             
             import groovy.transform.CompileStatic
             
             @CompileStatic
             class A {
                 def m(int a, int b) {
                 }
                 def m2(List<Integer> a) {
                     m(*1.with{[2, 3]})
                 }
             }
             """, """
             
             import groovy.transform.CompileStatic
             
             @CompileStatic
             class A {
                 def m(int a, int b) {
                 }
                 def m2(List<Integer> a) {
                     def integers = 1.with { [2, 3] }
                     m(integers[0], integers[1])
                 }
             }
             """);
  }

  private void doTest(final String before, final String after) {
    LightJavaCodeInsightTestFixture myFixture = highlighting.get().getFixture();
    myFixture.configureByText("_.groovy", before);
    myFixture.enableInspections(highlighting.get().getCustomInspections());
    List<IntentionAction> fixes = myFixture.getAllQuickFixes("_.groovy");
    assertEquals(1, fixes.size(), before);
    myFixture.launchAction(ContainerUtil.getFirstItem(fixes));
    myFixture.checkResult(after);
  }
}