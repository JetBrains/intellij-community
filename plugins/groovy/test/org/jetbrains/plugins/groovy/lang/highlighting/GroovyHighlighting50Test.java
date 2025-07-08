// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.lang.highlighting;

import com.intellij.testFramework.LightProjectDescriptor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.GroovyProjectDescriptors;
import org.jetbrains.plugins.groovy.LightGroovyTestCase;
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
}
