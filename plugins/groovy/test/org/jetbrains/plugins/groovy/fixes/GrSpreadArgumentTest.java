// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.fixes;

import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.testFramework.LightProjectDescriptor;
import org.codehaus.groovy.runtime.DefaultGroovyMethods;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.GroovyProjectDescriptors;
import org.jetbrains.plugins.groovy.lang.highlighting.GrHighlightingTestBase;

import java.util.List;

public class GrSpreadArgumentTest extends GrHighlightingTestBase {
  @Override
  @NotNull
  public final LightProjectDescriptor getProjectDescriptor() {
    return GroovyProjectDescriptors.GROOVY_LATEST;
  }

  @Override
  public void setUp() throws Exception {
    super.setUp();
  }

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
    myFixture.configureByText("_.groovy", before);
    myFixture.enableInspections(getCustomInspections());
    List<IntentionAction> fixes = myFixture.getAllQuickFixes("_.groovy");
    assert fixes.size() == 1 : before;
    myFixture.launchAction(DefaultGroovyMethods.first(fixes));
    myFixture.checkResult(after);
  }
}