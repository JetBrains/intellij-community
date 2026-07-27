// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.lang.highlighting;

import com.intellij.testFramework.LightProjectDescriptor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.GroovyProjectDescriptors;
import org.jetbrains.plugins.groovy.LightGroovyTestCase;
import org.jetbrains.plugins.groovy.util.HighlightingTest;

/**
 * @author Bas Leijdekkers
 */
public class GroovyHighlighting60Test extends LightGroovyTestCase implements HighlightingTest {

  @Override
  public void setUp() throws Exception {
    super.setUp();
  }

  @Override
  protected @NotNull LightProjectDescriptor getProjectDescriptor() {
    return GroovyProjectDescriptors.GROOVY_6_0;
  }

  public void testSimpleVal() {
    highlightingTest("""
                       val x = 1
                       for (val y : [1, 2, 3]) {
                         println y
                       }
                       class X {
                         var
                           x = 2
                       }
                       """);
  }
}
