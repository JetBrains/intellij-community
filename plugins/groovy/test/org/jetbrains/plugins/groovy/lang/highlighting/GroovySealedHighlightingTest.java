// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.lang.highlighting;

import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.GroovyProjectDescriptors;
import org.jetbrains.plugins.groovy.LightGroovyFixtures;
import org.jetbrains.plugins.groovy.codeInspection.bugs.GrPermitsClauseInspection;
import org.jetbrains.plugins.groovy.util.HighlightingTest;
import org.junit.jupiter.api.Test;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static org.jetbrains.plugins.groovy.LightGroovyFixtures.lightGroovyFixture;

@TestApplication
public class GroovySealedHighlightingTest implements HighlightingTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture(GroovyProjectDescriptors.GROOVY_4_0_REAL_JDK);
  private final TestFixture<LightJavaCodeInsightTestFixture> codeInsightFixture = lightGroovyFixture(projectFixture);

  @Override
  public @NotNull LightJavaCodeInsightTestFixture getFixture() {
    return codeInsightFixture.get();
  }

  @Override
  public String getTestName() {
    return LightGroovyFixtures.groovyTestName(codeInsightFixture.get());
  }

  @Test
  public void testPermitsWithoutSealed() {
    highlightingTest("""
                       class A <error>permits</error> B {}
                       class B extends A {}
                       """);
  }

  @Test
  public void testExclusiveness() {
    highlightingTest("""
                       <error>sealed</error> <error>non-sealed</error> class A {}
                       class B extends A {}""");
  }

  @Test
  public void testSealedEnum() {
    highlightingTest("<error>sealed</error> enum A {}");
  }

  @Test
  public void testSealedClassWithoutPermittedSubclasses() {
    highlightingTest("<error>sealed</error> interface A {}");
  }

  @Test
  public void testPermitsWithNonExtendingReference() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    highlightingTest("""
                       sealed class A permits <error>B<caret></error> {}
                       class B {}
                       """, GrPermitsClauseInspection.class);
    myFixture.launchAction(myFixture.findSingleIntention("Add 'A' to extends clause of 'B'"));
    myFixture.checkResult("""
                            sealed class A permits B {}
                            class B extends A {}
                            """);
  }

  @Test
  public void testExtendingWithoutPermission() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    highlightingTest("""
                       sealed class A permits B {}
                       class B extends A {}
                       class C extends <error>A<caret></error> {}""");
    myFixture.launchAction(myFixture.findSingleIntention("Add 'C' to permits clause of 'A'"));
    myFixture.checkResult("""
                            sealed class A permits B, C {}
                            class B extends A {}
                            class C extends A {}""");
  }

  @Test
  public void testImplementingWithoutPermission() {
    highlightingTest("""
                       sealed trait A permits B, C {}
                       class B implements A {}
                       interface C extends A {}
                       class D implements <error>A</error> {}""");
  }

  @Test
  public void testNonSealedClassWithoutSealedSuperclass() {
    highlightingTest("<error>non-sealed</error> class A {}");
  }

  @Test
  public void testSealedAnnotation() {
    highlightingTest("""
                       import groovy.transform.Sealed

                       <error>@Sealed</error>
                       <error>non-sealed</error> class A {}
                       """);
  }

  @Test
  public void testMentionClassInAnnotation() {
    highlightingTest("""
                       import groovy.transform.Sealed

                       @Sealed(permittedSubclasses = [B])
                       class A {}

                       class B extends A {}
                       class C extends <error>A</error> {}""");
  }

  @Test
  public void testNonExtendingClassInAnnotation() {
    highlightingTest("""
                       import groovy.transform.Sealed

                       @Sealed(permittedSubclasses = [<error>B</error>])
                       class A {}

                       class B {}
                       """, GrPermitsClauseInspection.class);
  }

  @Test
  public void testGenericSealedInterface() {
    highlightingTest("""
                       sealed interface A<T> {}

                       class B implements A {}
                       class C<T> implements A<T> {}
                       """);
  }
}
