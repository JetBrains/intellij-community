// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.codeInspection.confusing

import com.intellij.codeInspection.LocalInspectionTool
import groovy.transform.CompileStatic
import org.jetbrains.plugins.groovy.util.ActionTest
import org.jetbrains.plugins.groovy.util.GroovyLatestTest
import org.jetbrains.plugins.groovy.util.HighlightingTest
import org.junit.Test

@CompileStatic
class GroovyPointlessBooleanInspectionTest extends GroovyLatestTest implements HighlightingTest, ActionTest {

  @Override
  Collection<? extends Class<? extends LocalInspectionTool>> getInspections() {
    [GroovyPointlessBooleanInspection]
  }

  @Test
  void 'simple unary'() {
    doTest($/<warning descr="Redundant boolean operations">!false</warning>/$, 'true')
    doTest($/<warning descr="Redundant boolean operations">!true</warning>/$, 'false')
  }

  @Test
  void 'double-negation unary'() {
    doTest($/<warning descr="Redundant boolean operations">!!false</warning>/$, 'false')
    doTest($/<warning descr="Redundant boolean operations">!!true</warning>/$, 'true')
  }

  @Test
  void 'triple-negation unary'() {
    doTest($/!<caret><warning descr="Redundant boolean operations">!!false</warning>/$, '!false')
    doTest($/!<caret><warning descr="Redundant boolean operations">!!true</warning>/$, '!true')
  }

  @Test
  void 'non-primitive type'() {
    highlightingTest('Boolean x = null; x == true')
  }

  private void doTest(String before, String after) {
    highlightingTest before
    doActionTest('Simplify', after)
  }
}
