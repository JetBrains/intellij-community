// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.lang.psi;

import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.jetbrains.plugins.groovy.GroovyProjectDescriptors;
import org.jetbrains.plugins.groovy.intentions.GrIntentionFixture;
import org.junit.jupiter.api.Test;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static org.jetbrains.plugins.groovy.intentions.GrIntentionFixtureKt.grIntentionFixture;

@TestApplication
public class GrIntentions40Test {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture(GroovyProjectDescriptors.GROOVY_4_0);
  private final TestFixture<GrIntentionFixture> grIntention = grIntentionFixture(projectFixture, null, null);

  @Test
  public void testQualifyCall() {
    LightJavaCodeInsightTestFixture myFixture = grIntention.get().getFixture();
    myFixture.addClass("""
      public interface Bar {
        static void foo() {
          System.out.println("20");
        }
      }
    """);
    grIntention.get().doTextTest("""
                 
                 class A implements Bar {
                     void bar() {
                         f<caret>oo()
                     }
                 }
                 """, "Replace with", """
                 
                 class A implements Bar {
                     void bar() {
                         Bar.foo()
                     }
                 }
                 """);
  }
}
