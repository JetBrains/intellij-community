// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.codeInspection.control

import com.intellij.testFramework.LightProjectDescriptor
import groovy.transform.CompileStatic
import org.jetbrains.plugins.groovy.GroovyProjectDescriptors
import org.jetbrains.plugins.groovy.codeInspection.validity.GroovyDuplicateSwitchBranchInspection
import org.jetbrains.plugins.groovy.lang.highlighting.GrHighlightingTestBase
import org.jetbrains.plugins.groovy.util.TestUtils

@CompileStatic
class GroovyDuplicateSwitchBranchInspectionTest extends GrHighlightingTestBase {

  LightProjectDescriptor projectDescriptor = GroovyProjectDescriptors.GROOVY_LATEST
  String basePath = TestUtils.testDataPath + "inspections/identicalSwitchBranches/"

  @Override
  void setUp() throws Exception {
    super.setUp()
    fixture.enableInspections(GroovyDuplicateSwitchBranchInspection)
  }

  void testSwitch() { doTest() }
}
