// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.lang.highlighting;

import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.jetbrains.plugins.groovy.GroovyBundle;
import org.jetbrains.plugins.groovy.codeInspection.confusing.ClashingTraitMethodsInspection;
import org.jetbrains.plugins.groovy.intentions.GrIntentionFixture;
import org.junit.jupiter.api.Test;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static org.jetbrains.plugins.groovy.intentions.GrIntentionFixtureKt.grIntentionFixture;

/**
 * Created by Max Medvedev on 09/06/14
 */
@TestApplication
public class ClashingTraitMethodsQuickFixTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture();
  private final TestFixture<GrIntentionFixture> grIntention =
    grIntentionFixture(projectFixture, GroovyBundle.message("declare.explicit.implementations.of.trait"), null, ClashingTraitMethodsInspection.class);


  /**
   * Somewhat incorrect, the overriding method return type should be `def` instead of `Object`
   */
  @Test
  public void testQuickFix() {
    grIntention.get().doTextTest("""
                 trait T1 {
                     def foo(){}
                 }

                 trait T2 {
                     def foo(){}
                 }

                 class <caret>A implements T1, T2 {

                 }
                 """, """
                 trait T1 {
                     def foo(){}
                 }

                 trait T2 {
                     def foo(){}
                 }

                 class A implements T1, T2 {

                     @Override
                     Object foo() {
                         return T2.super.foo()
                     }
                 }
                 """);
  }
}
