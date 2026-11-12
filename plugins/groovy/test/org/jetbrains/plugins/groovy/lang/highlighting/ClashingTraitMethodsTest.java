// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.lang.highlighting;

import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.jetbrains.plugins.groovy.GroovyProjectDescriptors;
import org.jetbrains.plugins.groovy.codeInspection.confusing.ClashingTraitMethodsInspection;
import org.junit.jupiter.api.Test;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static org.jetbrains.plugins.groovy.lang.highlighting.GrHighlightingFixtureKt.grHighlightingFixture;

/**
 * Created by Max Medvedev on 09/06/14
 */
@TestApplication
public class ClashingTraitMethodsTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture(GroovyProjectDescriptors.GROOVY_3_0);
  private final TestFixture<GrHighlightingFixture> highlighting =
    grHighlightingFixture(projectFixture, GrHighlightingFixture.getHighlightingTestDataPath(), () -> new ClashingTraitMethodsInspection[]{new ClashingTraitMethodsInspection()});

  @Test
  public void testClash() {
    highlighting.get().doTestHighlighting("""
                         trait T1 {
                           def foo(){}
                         }

                         trait T2 {
                           def foo(){}
                         }

                         class <warning descr="Traits T1, T2 contain clashing methods with signature foo()">A</warning> implements T1, T2 {

                         }
                         """);
  }

  @Test
  public void testCustomImplementationNoClash() {
    highlighting.get().doTestHighlighting("""
                         trait T1 {
                           def foo(){}
                         }

                         trait T2 {
                           def foo(){}
                         }

                         class A implements T1, T2 {
                             def foo() {}
                         }
                         """);
  }

  @Test
  public void testNoClash() {
    highlighting.get().doTestHighlighting("""
                         trait T1 {
                           def foo(){}
                         }

                         trait T2 {
                           abstract def foo()
                         }

                         class A implements T1, T2 {
                         }
                         """);
  }

  @Test
  public void testNoClashWithInterface() {
    highlighting.get().doTestHighlighting("""
                         trait T1 {
                           def foo(){}
                         }

                         interface T2 {
                           def foo()
                         }

                         class A implements T1, T2 {
                         }
                         """);
  }

  @Test
  public void testClashWithDefaultMethodInterfaces() {
    highlighting.get().doTestHighlighting("""
                         interface T1 {
                           default foo(){}
                         }

                         interface T2 {
                           default foo() {}
                         }

                         class <warning descr="Traits T1, T2 contain clashing methods with signature foo()">A</warning> implements T1, T2 {
                         }
                         """);
  }

  @Test
  public void testClashTraitWithDefaultMethodInterface() {
    highlighting.get().doTestHighlighting("""
                         trait T1 {
                           def foo(){}
                         }

                         interface T2 {
                           default foo() {}
                         }

                         class <warning descr="Traits T1, T2 contain clashing methods with signature foo()">A</warning> implements T1, T2 {
                         }
                         """);
  }

  @Test
  public void testNoClashInInheritor() {
    highlighting.get().doTestHighlighting("""
                         trait T1 {
                           def foo(){}
                         }

                         interface T2 {
                           def foo()
                         }

                         class A implements T1, T2 {
                         }

                         class B extends A{}
                         """);
  }
}
