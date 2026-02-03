// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.lang.highlighting;

import com.intellij.codeInspection.InspectionProfileEntry;
import org.jetbrains.plugins.groovy.codeInspection.assignment.GroovyAssignabilityCheckInspection;

public class GrTypeCheckHighlightingTest extends GrHighlightingTestBase {
  @Override
  public String getBasePath() { return super.getBasePath() + "typecheck/"; }

  @Override
  public InspectionProfileEntry[] getCustomInspections() {
    return new InspectionProfileEntry[]{new GroovyAssignabilityCheckInspection()};
  }

  public void testTypeCheckClass() { doTest(); }

  public void testTypeCheckBool() { doTest(); }

  public void testTypeCheckChar() { doTest(); }

  public void testTypeCheckEnum() { doTest(); }

  public void testTypeCheckString() { doTest(); }

  public void testCastBuiltInTypes() { doTest(); }

  public void doTest() {
    addBigDecimal();
    addBigInteger();
    super.doTest();
  }

  public void testBoxPrimitiveTypesInListLiterals() {
    doTestHighlighting("""
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

  public void testMapWithoutStringKeysAndValues() {
    doTestHighlighting("""
                         def foo(int a) {}
                         def m = [(aa): (<error descr="<expression> expected, got ')'">)</error>]
                         foo<warning descr="'foo' in '_' cannot be applied to '(java.util.LinkedHashMap)'">(m)</warning>
                         """);
  }

  public void testAssignmentWhenGetterAndSetterHaveDifferentTypes() {
    doTestHighlighting("""
                         interface MavenArtifactRepository {
                           URI getUrl()
                           void setUrl(Object var1)
                         }
                         def test(MavenArtifactRepository m) {
                           m.url = "String"
                         }
                         """);
  }

  public void testParameterWithSingleCharacterStringInitializer() {
    doTestHighlighting("@groovy.transform.CompileStatic def ff(char c = '\\n') {}");
  }

  public void testAmbiguousCallCompileStatic() {
    doTestHighlighting("""
                         def ff(String s, Object o) {}
                         def ff(Object o, String s) {}

                         @groovy.transform.CompileStatic
                         def usage() {
                           ff<error descr="Method call is ambiguous">("", "")</error>
                         }
                         """);
  }

  public void testNoWarningForTypeParameterAssigning() {
    doTestHighlighting("""
                         class A<T> {
                         
                             T value
                             def foo(it) {
                                 value = it
                             }
                         
                         }
                         """);
  }
}
