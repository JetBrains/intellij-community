// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.intentions;

import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.jetbrains.plugins.groovy.codeInspection.bugs.GrSwitchExhaustivenessCheckInspection;
import org.junit.jupiter.api.Test;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaCodeInsightFixture;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;

@TestApplication
public class AddMissingCaseSectionsTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture();
  private final TestFixture<LightJavaCodeInsightTestFixture> codeInsightFixture = lightJavaCodeInsightFixture(projectFixture);

  public void doTest(String before, String after) {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.enableInspections(GrSwitchExhaustivenessCheckInspection.class);
    myFixture.configureByText("_.groovy", before);
    myFixture.launchAction(myFixture.findSingleIntention("Insert"));
    myFixture.checkResult(after);
  }

  @Test
  public void test_boolean() {
    doTest("""
             def foo(boolean b) {
               def x = swit<caret>ch (b) {
                   case true -> 1
               }
             }
             """, """
             def foo(boolean b) {
               def x = swit<caret>ch (b) {
                   case true -> 1
                   case false -> throw new IllegalStateException()
               }
             }
             """);
  }

  @Test
  public void test_enum() {
    doTest("""
             enum A { X, Y }
             
             def foo(A b) {
               def x = swit<caret>ch (b) {
                   case A.X -> 1
               }
             }
             """, """
             enum A { X, Y }
             
             def foo(A b) {
               def x = swit<caret>ch (b) {
                   case A.X -> 1
                   case A.Y -> throw new IllegalStateException()
               }
             }
             """);
  }

  @Test
  public void test_sealed() {
    doTest("""
             abstract sealed class A {}
             class B extends A {}
             class C extends A {}
             class D extends A {}
             
             def foo(A b) {
               def x = swit<caret>ch (b) {
                   case B -> 1
               }
             }
             """, """
             abstract sealed class A {}
             class B extends A {}
             class C extends A {}
             class D extends A {}
             
             def foo(A b) {
               def x = swit<caret>ch (b) {
                   case B -> 1
                   case C -> throw new IllegalStateException()
                   case D -> throw new IllegalStateException()
               }
             }
             """);
  }
}
