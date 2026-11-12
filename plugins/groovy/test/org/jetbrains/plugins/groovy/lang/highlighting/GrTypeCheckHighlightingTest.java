// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.lang.highlighting;

import com.intellij.codeInspection.InspectionProfileEntry;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.jetbrains.plugins.groovy.GroovyProjectDescriptors;
import org.jetbrains.plugins.groovy.LightGroovyFixtures;
import org.jetbrains.plugins.groovy.codeInspection.assignment.GroovyAssignabilityCheckInspection;
import org.junit.jupiter.api.Test;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static org.jetbrains.plugins.groovy.lang.highlighting.GrHighlightingFixtureKt.grHighlightingFixture;

@TestApplication
public class GrTypeCheckHighlightingTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture(GroovyProjectDescriptors.GROOVY_2_1);
  private final TestFixture<GrHighlightingFixture> highlighting =
    grHighlightingFixture(projectFixture, GrHighlightingFixture.getHighlightingTestDataPath() + "typecheck/", () -> new InspectionProfileEntry[]{new GroovyAssignabilityCheckInspection()});

  @Test
  public void testTypeCheckClass() { doTest(); }

  @Test
  public void testTypeCheckBool() { doTest(); }

  @Test
  public void testTypeCheckChar() { doTest(); }

  @Test
  public void testTypeCheckEnum() { doTest(); }

  @Test
  public void testTypeCheckString() { doTest(); }

  @Test
  public void testCastBuiltInTypes() { doTest(); }

  private void doTest() {
    LightJavaCodeInsightTestFixture myFixture = highlighting.get().getFixture();
    LightGroovyFixtures.addBigDecimal(myFixture);
    LightGroovyFixtures.addBigInteger(myFixture);
    highlighting.get().doTest();
  }

  @Test
  public void testBoxPrimitiveTypesInListLiterals() {
    highlighting.get().doTestHighlighting("""
                         void method(List<Integer> ints) {}
                         void method2(Map<String, Integer> map) {}

                         interface X {
                             int C = 0
                             int D = 1
                         }

                         method([X.C, X.D])
                         method2([a: X.C, b: X.D])
                         """);
  }

  @Test
  public void testMapWithoutStringKeysAndValues() {
    highlighting.get().doTestHighlighting("""
                         def foo(int a) {}
                         def m = [(aa): (<error descr="<expression> expected, got ')'">)</error>]
                         foo<warning descr="'foo' in '_' cannot be applied to '(java.util.LinkedHashMap)'">(m)</warning>
                         """);
  }

  @Test
  public void testAssignmentWhenGetterAndSetterHaveDifferentTypes() {
    highlighting.get().doTestHighlighting("""
                         interface MavenArtifactRepository {
                           URI getUrl()
                           void setUrl(Object var1)
                         }
                         def test(MavenArtifactRepository m) {
                           m.url = "String"
                         }
                         """);
  }

  @Test
  public void testParameterWithSingleCharacterStringInitializer() {
    highlighting.get().doTestHighlighting("@groovy.transform.CompileStatic def ff(char c = '\\n') {}");
  }

  @Test
  public void testAmbiguousCallCompileStatic() {
    highlighting.get().doTestHighlighting("""
                         def ff(String s, Object o) {}
                         def ff(Object o, String s) {}

                         @groovy.transform.CompileStatic
                         def usage() {
                           ff<error descr="Method call is ambiguous">("", "")</error>
                         }
                         """);
  }

  @Test
  public void testNoWarningForTypeParameterAssigning() {
    highlighting.get().doTestHighlighting("""
                         class A<T> {
                         
                             T value
                             def foo(it) {
                                 value = it
                             }
                         
                         }
                         """);
  }
}
