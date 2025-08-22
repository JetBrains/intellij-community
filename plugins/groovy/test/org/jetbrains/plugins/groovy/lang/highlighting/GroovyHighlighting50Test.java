// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.lang.highlighting;

import com.intellij.testFramework.LightProjectDescriptor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.GroovyProjectDescriptors;
import org.jetbrains.plugins.groovy.LightGroovyTestCase;
import org.jetbrains.plugins.groovy.codeInspection.assignment.GroovyAssignabilityCheckInspection;
import org.jetbrains.plugins.groovy.util.HighlightingTest;

public class GroovyHighlighting50Test extends LightGroovyTestCase implements HighlightingTest {
  @Override
  protected @NotNull LightProjectDescriptor getProjectDescriptor() {
    return GroovyProjectDescriptors.GROOVY_5_0;
  }

  public void testPatternVariable() {
    highlightingTest("""
                   class X {
                     static class A {}
                     static class B extends A{}
                   
                     void simple() {
                       A a = new B()
                       if (a instanceof B <error descr="Variable 'a' already defined">a</error>) {}
                     }
                   
                     void externalVariable() {
                      A a = new B()
                      int c;
                      if (a instanceof B <error descr="Variable 'c' already defined">c</error>) {}
                     }
                   
                     void parameter(int c) {
                      A a = new B()
                      if (a instanceof B <error descr="Variable 'c' already defined">c</error>) {}
                     }
                   }
                   """);
  }

  public void testIncompatibleTypeOfArrayInitializer() {
    addCompileStatic();
    highlightingTest("""
                       import groovy.transform.CompileStatic
                       
                       class A {}
                       
                       static void main(String[] args) {
                          def a = new String[]{
                          <warning descr="Illegal initializer for 'String'">{"a"}</warning>,
                          <warning descr="Illegal initializer for 'String'">{}</warning>,
                          "foo"
                          }
                       
                          def b = new A[][]{<warning descr="Cannot assign 'String' to 'A[]'">"a"</warning>}
                       
                          def c = new A[]{
                          <warning descr="Cannot assign 'Integer' to 'A'">1</warning>
                          }
                       
                          def d = new A[][]{
                          {},
                          {<warning descr="Cannot assign 'Object' to 'A'">new Object()</warning>}
                          }
                       
                          def e = new String[][]{"str", 1, {"strInsideInitializer"}}
                       }
                       
                       @CompileStatic
                       void anotherMain() {
                          def a = new String[]{
                          <error descr="Illegal initializer for 'String'">{"a"}</error>,
                          <error descr="Illegal initializer for 'String'">{}</error>,
                          "foo"
                          }
                       
                          def b = new A[][]{<error descr="Cannot assign 'String' to 'A[]'">"a"</error>}
                       
                          def c = new A[]{
                          <error descr="Cannot assign 'Integer' to 'A'">1</error>
                          }
                       
                          def d = new A[][]{
                          {},
                          {<error descr="Cannot assign 'Object' to 'A'">new Object()</error>}
                          }
                       
                          def e = new String[][]{
                          <error descr="Cannot assign 'String' to 'String[]'">"str"</error>,
                          <error descr="Cannot assign 'Integer' to 'String[]'">1</error>,
                          {"strInsideInitializer"}
                          }
                       }
                       """, GroovyAssignabilityCheckInspection.class);
  }
}
