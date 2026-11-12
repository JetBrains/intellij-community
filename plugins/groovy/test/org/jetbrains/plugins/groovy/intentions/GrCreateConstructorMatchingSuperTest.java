// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.intentions;

import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.junit.jupiter.api.Test;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static org.jetbrains.plugins.groovy.intentions.GrIntentionFixtureKt.grIntentionFixture;

/**
 * @author Max Medvedev
 */
@TestApplication
public class GrCreateConstructorMatchingSuperTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture();
  private final TestFixture<GrIntentionFixture> grIntention = grIntentionFixture(projectFixture, "Create constructor matching super", null);

  @Test
  public void testSuperWithJavaDoc() {
    LightJavaCodeInsightTestFixture myFixture = grIntention.get().getFixture();
    myFixture.addClass("""
                         public class Base {
                             /**
                              * my doc
                              */
                              public Base(int x) {
                              }
                         }
                         """);

    grIntention.get().doTextTest("""
                 class <caret>Inheritor extends Base {
                 }
                 """,
               """
                 class Inheritor extends Base {
                     /**
                      * my doc
                      */
                     Inheritor(int x) {
                         super(x)
                     }
                 }
                 """);
  }

  @Test
  public void testInvalidOriginalParameterName() {
    LightJavaCodeInsightTestFixture myFixture = grIntention.get().getFixture();
    myFixture.addClass("""
                         class Base {
                           Base(String in, String s) {
                           }
                         }""");

    grIntention.get().doTextTest("""
                 class <caret>Inh extends Base {
                 }
                 """,
               """
                 class Inh extends Base {
                     Inh(String s, String s2) {
                         super(s, s2)
                     }
                 }
                 """);
  }
}
