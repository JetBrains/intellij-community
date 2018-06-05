// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.util

import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.testFramework.fixtures.CodeInsightTestFixture
import groovy.transform.CompileStatic

@CompileStatic
trait HighlightingTest {

  abstract CodeInsightTestFixture getFixture()

  abstract String getTestName()

  Collection<Class<? extends LocalInspectionTool>> getInspections() { [] }

  void highlightingTest() {
    fixture.enableInspections inspections
    fixture.testHighlighting testName + '.groovy'
  }
}
