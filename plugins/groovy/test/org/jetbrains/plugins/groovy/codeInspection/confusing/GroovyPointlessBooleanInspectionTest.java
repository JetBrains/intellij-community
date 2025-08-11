// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.codeInspection.confusing;

import com.intellij.codeInspection.LocalInspectionTool;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.util.ActionTest;
import org.jetbrains.plugins.groovy.util.Groovy50Test;
import org.jetbrains.plugins.groovy.util.GroovyLatestTest;
import org.jetbrains.plugins.groovy.util.HighlightingTest;
import org.junit.Test;

import java.util.Collection;
import java.util.List;

public class GroovyPointlessBooleanInspectionTest extends Groovy50Test implements HighlightingTest, ActionTest {

  @Override
  public @NotNull Collection<Class<? extends LocalInspectionTool>> getInspections() {
    return List.of(GroovyPointlessBooleanInspection.class);
  }

  @Test
  public void simpleUnary() {
    doTest("<warning descr=\"Redundant boolean operations\">!false</warning>", "true");
    doTest("<warning descr=\"Redundant boolean operations\">!true</warning>", "false");
  }

  @Test
  public void doubleNegationUnary() {
    doTest("<warning descr=\"Redundant boolean operations\">!!false</warning>", "false");
    doTest("<warning descr=\"Redundant boolean operations\">!!true</warning>", "true");
  }

  @Test
  public void tripleNegationUnary() {
    doTest("!<caret><warning descr=\"Redundant boolean operations\">!!false</warning>", "!false");
    doTest( "!<caret><warning descr=\"Redundant boolean operations\">!!true</warning>", "!true");
  }

  @Test
  public void implicationExpression() {
    doTest("<warning descr=\"Redundant boolean operations\">false ==> (false)</warning>", "(false)");
    doTest( "<warning descr=\"Redundant boolean operations\">(false) ==> true</warning>", "(false)");
  }

  @Test
  public void nonPrimitiveType() {
    highlightingTest("Boolean x = null; x == true");
  }

  private void doTest(String before, String after) {
    highlightingTest(before);
    doActionTest("Simplify", after);
  }
}
