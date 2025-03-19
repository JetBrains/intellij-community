// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.lang.highlighting;

import com.intellij.testFramework.LightProjectDescriptor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.GroovyProjectDescriptors;

public class GrHighlightingAttributesTest extends GrHighlightingTestBase {
  public void testHighlightingAttributes() {
    getFixture().testHighlighting(false, true, false, "highlightingAttributes/test.groovy");
  }

  public void testTodoHighlighting() {
    getFixture().testHighlighting(false, true, false, "highlightingAttributes/todo.groovy");
  }

  @Override
  public final @NotNull LightProjectDescriptor getProjectDescriptor() {
    return GroovyProjectDescriptors.GROOVY_LATEST;
  }
}
