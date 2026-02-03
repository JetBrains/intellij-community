// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.codeInspection.confusing;

import com.intellij.codeInspection.LocalInspectionTool;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.util.GroovyLatestTest;
import org.jetbrains.plugins.groovy.util.HighlightingTest;
import org.junit.Test;

import java.util.Collection;
import java.util.List;

public class UnnecessaryQualifiedReferenceInspectionTest extends GroovyLatestTest implements HighlightingTest {
  @Test
  public void attributeExpression() {
    highlightingTest("""
                       class A { static foo }
                       A.@foo
                       A.@foo()
                       """);
  }

  @Override
  public final @NotNull Collection<Class<? extends LocalInspectionTool>> getInspections() {
    return List.of(UnnecessaryQualifiedReferenceInspection.class);
  }
}
