// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.lang.highlighting;

import com.intellij.testFramework.LightProjectDescriptor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.GroovyProjectDescriptors;
import org.jetbrains.plugins.groovy.LightGroovyTestCase;
import org.jetbrains.plugins.groovy.codeInspection.confusing.UnnecessaryQualifiedReferenceInspection;
import org.jetbrains.plugins.groovy.util.HighlightingTest;

public class GroovyHighlighting40Test extends LightGroovyTestCase implements HighlightingTest {
  public void testPermitsWithoutSealed() {
    myFixture.addClass("""
                          public interface Bar {
                              static void foo() {
                                  System.out.println("20");
                              }
                          }
                          """);
    myFixture.enableInspections(UnnecessaryQualifiedReferenceInspection.class);
    highlightingTest("""
                       class A implements Bar {
                           void bar() {
                               Bar.foo()
                           }
                       }
                       """);
  }

  public void testVarInForLoop() {
    highlightingTest("for (var i : []) {}");
  }

  @Override
  public final @NotNull LightProjectDescriptor getProjectDescriptor() {
    return GroovyProjectDescriptors.GROOVY_4_0;
  }
}
