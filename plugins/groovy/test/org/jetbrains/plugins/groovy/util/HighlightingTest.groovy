// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.util

import com.intellij.testFramework.fixtures.CodeInsightTestFixture
import groovy.transform.CompileStatic
import org.jetbrains.plugins.groovy.codeInspection.assignment.GroovyAssignabilityCheckInspection
import org.jetbrains.plugins.groovy.codeInspection.untypedUnresolvedAccess.GrUnresolvedAccessInspection

@CompileStatic
trait HighlightingTest {

  abstract CodeInsightTestFixture getFixture()

  abstract String getTestName()

  void highlightingTest() {
    fixture.enableInspections GrUnresolvedAccessInspection, GroovyAssignabilityCheckInspection
    fixture.testHighlighting testName + '.groovy'
  }
}
