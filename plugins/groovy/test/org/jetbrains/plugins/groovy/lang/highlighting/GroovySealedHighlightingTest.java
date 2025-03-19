// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.lang.highlighting;

import com.intellij.testFramework.LightProjectDescriptor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.GroovyProjectDescriptors;
import org.jetbrains.plugins.groovy.LightGroovyTestCase;
import org.jetbrains.plugins.groovy.codeInspection.bugs.GrPermitsClauseInspection;
import org.jetbrains.plugins.groovy.util.HighlightingTest;

public class GroovySealedHighlightingTest extends LightGroovyTestCase implements HighlightingTest {
  public void testPermitsWithoutSealed() {
    highlightingTest("""
                       class A <error>permits</error> B {}
                       class B extends A {}
                       """);
  }

  public void testExclusiveness() {
    highlightingTest("""
                       <error>sealed</error> <error>non-sealed</error> class A {}
                       class B extends A {}""");
  }

  public void testSealedEnum() {
    highlightingTest("<error>sealed</error> enum A {}");
  }

  public void testSealedClassWithoutPermittedSubclasses() {
    highlightingTest("<error>sealed</error> interface A {}");
  }

  public void testPermitsWithNonExtendingReference() {
    highlightingTest("""
                       sealed class A permits <error>B</error> {}
                       class B {}
                       """, GrPermitsClauseInspection.class);
  }

  public void testExtendingWithoutPermission() {
    highlightingTest("""
                       sealed class A permits B {}
                       class B extends A {}
                       class C extends <error>A</error> {}""");
  }

  public void testImplementingWithoutPermission() {
    highlightingTest("""
                       sealed trait A permits B, C {}
                       class B implements A {}
                       interface C extends A {}
                       class D implements <error>A</error> {}""");
  }

  public void testNonSealedClassWithoutSealedSuperclass() {
    highlightingTest("<error>non-sealed</error> class A {}");
  }

  public void testSealedAnnotation() {
    highlightingTest("""
                       import groovy.transform.Sealed

                       <error>@Sealed</error>
                       <error>non-sealed</error> class A {}
                       """);
  }

  public void testMentionClassInAnnotation() {
    highlightingTest("""
                       import groovy.transform.Sealed

                       @Sealed(permittedSubclasses = [B])
                       class A {}

                       class B extends A {}
                       class C extends <error>A</error> {}""");
  }

  public void testNonExtendingClassInAnnotation() {
    highlightingTest("""
                       import groovy.transform.Sealed

                       @Sealed(permittedSubclasses = [<error>B</error>])
                       class A {}

                       class B {}
                       """, GrPermitsClauseInspection.class);
  }

  public void testGenericSealedInterface() {
    highlightingTest("""
                       sealed interface A<T> {}

                       class B implements A {}
                       class C<T> implements A<T> {}
                       """);
  }

  @Override
  public final @NotNull LightProjectDescriptor getProjectDescriptor() {
    return GroovyProjectDescriptors.GROOVY_4_0_REAL_JDK;
  }
}
