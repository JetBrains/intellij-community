// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.fixes;

import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInspection.InspectionProfileEntry;
import com.intellij.testFramework.LightProjectDescriptor;
import org.codehaus.groovy.runtime.DefaultGroovyMethods;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.GroovyProjectDescriptors;
import org.jetbrains.plugins.groovy.codeInspection.assignment.GroovyAssignabilityCheckInspection;
import org.jetbrains.plugins.groovy.lang.highlighting.GrHighlightingTestBase;

import java.util.List;

public class GrMultipleAssignmentTest extends GrHighlightingTestBase {
  @Override
  public InspectionProfileEntry[] getCustomInspections() {
    return new GroovyAssignabilityCheckInspection[]{new GroovyAssignabilityCheckInspection()};
  }

  @Override
  @NotNull
  public final  LightProjectDescriptor getProjectDescriptor() {
    return GroovyProjectDescriptors.GROOVY_LATEST;
  }

  @Override
  public void setUp() throws Exception {
    super.setUp();
  }

  public void testReplaceListWithLiteral() {
    doTest("""
             
             import groovy.transform.CompileStatic
             
             @CompileStatic
             def foo() {
                 def list = [1, 2]
                 def (a, b) = l<caret>ist
             }
             """, """
             
             import groovy.transform.CompileStatic
             
             @CompileStatic
             def foo() {
                 def list = [1, 2]
                 def (a, b) = [list[0], list[1]]
             }
             """);
  }

  public void testReplaceCallWithLiteral() {
    doTest("""
             
             import groovy.transform.CompileStatic
             
             @CompileStatic
             def foo() {
                 def (a, b) = bar()
             }
             
             def bar() { [] }
             """, """
             
             import groovy.transform.CompileStatic
             
             @CompileStatic
             def foo() {
                 def objects = bar()
                 def (a, b) = [objects[0], objects[1]]
             }
             
             def bar() { [] }
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