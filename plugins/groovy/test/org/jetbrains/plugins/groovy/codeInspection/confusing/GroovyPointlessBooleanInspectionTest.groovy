// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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
    doTest($/<warning descr="!false can be simplified to 'true'">!false</warning>/$, 'true')
    doTest($/<warning descr="!true can be simplified to 'false'">!true</warning>/$, 'false')
  }

  @Test
  void 'double-negation unary'() {
    doTest($/<warning descr="!!false can be simplified to 'false'">!!false</warning>/$, 'false')
    doTest($/<warning descr="!!true can be simplified to 'true'">!!true</warning>/$, 'true')
  }

  @Test
  void 'triple-negation unary'() {
    doTest($/!<caret><warning descr="!!false can be simplified to 'false'">!!false</warning>/$, '!false')
    doTest($/!<caret><warning descr="!!true can be simplified to 'true'">!!true</warning>/$, '!true')
  }

  private void doTest(String before, String after) {
    highlightingTest before
    doActionTest('Simplify', after)
  }
}
