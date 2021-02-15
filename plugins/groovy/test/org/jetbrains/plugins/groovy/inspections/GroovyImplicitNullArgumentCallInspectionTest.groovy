// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.inspections

import groovy.transform.CompileStatic
import org.jetbrains.plugins.groovy.codeInspection.confusing.GroovyImplicitNullArgumentCallInspection
import org.jetbrains.plugins.groovy.lang.highlighting.GrHighlightingTestBase

@CompileStatic
class GroovyImplicitNullArgumentCallInspectionTest extends GrHighlightingTestBase {

  private void doTest(String text) {
    myFixture.configureByText('_.groovy', text)

    myFixture.enableInspections(GroovyImplicitNullArgumentCallInspection)
    myFixture.checkHighlighting(true, false, true)
  }

  void testShowWeakWarning() {
    doTest '''
  def foo(x) {}
  
  foo<weak_warning>()</weak_warning>
'''
  }

  void testNoWarningIfNullWasPassedExplicitly() {
    doTest '''
  def foo(x) {}
  
  foo(null)
'''
  }
}
