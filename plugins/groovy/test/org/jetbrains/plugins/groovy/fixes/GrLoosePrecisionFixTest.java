// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.fixes;

import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.testFramework.LightProjectDescriptor;
import org.codehaus.groovy.runtime.DefaultGroovyMethods;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.GroovyProjectDescriptors;
import org.jetbrains.plugins.groovy.lang.highlighting.GrHighlightingTestBase;

import java.util.List;

public class GrLoosePrecisionFixTest extends GrHighlightingTestBase {
  @Override
  public void setUp() throws Exception {
    super.setUp();
  }

  @Override
  @NotNull
  public final LightProjectDescriptor getProjectDescriptor() {
    return GroovyProjectDescriptors.GROOVY_LATEST;
  }

  public void testIntByte() {
    doTest("""
             
             import groovy.transform.CompileStatic
             
             @CompileStatic
             class A {
                 def m2(int b) {
                     byte a = b
                 }
             }
             """, """
             
             import groovy.transform.CompileStatic
             
             @CompileStatic
             class A {
                 def m2(int b) {
                     byte a = (byte) b
                 }
             }
             """);
  }

  public void testDoubleFloat() {
    doTest("""
             
             import groovy.transform.CompileStatic
             
             @CompileStatic
             class A {
                 def m2(double b) {
                     float a = b
                 }
             }
             """, """
             
             import groovy.transform.CompileStatic
             
             @CompileStatic
             class A {
                 def m2(double b) {
                     float a = (float) b
                 }
             }
             """);
  }

  public void testBoxed() {
    doTest("""
             
             import groovy.transform.CompileStatic
             
             @CompileStatic
             class A {
                 def m2(Double b) {
                     Float a = b
                 }
             }
             """, """
             
             import groovy.transform.CompileStatic
             
             @CompileStatic
             class A {
                 def m2(Double b) {
                     Float a = (Float) b
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
