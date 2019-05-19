// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.codeInspection.control

import com.intellij.testFramework.LightProjectDescriptor
import groovy.transform.CompileStatic
import org.jetbrains.annotations.NotNull
import org.jetbrains.plugins.groovy.GroovyProjectDescriptors
import org.jetbrains.plugins.groovy.lang.highlighting.GrHighlightingTestBase
import org.jetbrains.plugins.groovy.util.TestUtils

@CompileStatic
class GroovyIdenticalBranchesInspectionTest extends GrHighlightingTestBase {

  LightProjectDescriptor projectDescriptor = GroovyProjectDescriptors.GROOVY_LATEST
  String basePath = TestUtils.testDataPath + "inspections/identicalBranches/"

  @Override
  void setUp() throws Exception {
    super.setUp()
    fixture.enableInspections(GroovyConditionalWithIdenticalBranchesInspection, GroovyIfStatementWithIdenticalBranchesInspection)
  }

  void 'test two new expressions'() { doTest() }

  void 'test empty list and map'() { doTest() }

  @NotNull
  @Override
  protected String getTestName(boolean lowercaseFirstLetter) {
    def name = getName()
    if (name.contains(" ")) {
      name = name.split(" ").collect { String it -> it == "test" ? it : it.capitalize() }.join('')
    }
    return getTestName(name, lowercaseFirstLetter)
  }
}
