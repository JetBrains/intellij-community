/*
 * Copyright 2000-2016 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.plugins.groovy.codeInspection.control

import com.intellij.testFramework.LightProjectDescriptor
import groovy.transform.CompileStatic
import org.jetbrains.annotations.NotNull
import org.jetbrains.plugins.groovy.GroovyLightProjectDescriptor
import org.jetbrains.plugins.groovy.lang.highlighting.GrHighlightingTestBase
import org.jetbrains.plugins.groovy.util.TestUtils

@CompileStatic
class GroovyIdenticalBranchesInspectionTest extends GrHighlightingTestBase {

  LightProjectDescriptor projectDescriptor = GroovyLightProjectDescriptor.GROOVY_LATEST
  String basePath = TestUtils.testDataPath + "inspections/identicalBranches/"

  @Override
  void setUp() throws Exception {
    super.setUp()
    fixture.enableInspections(GroovyConditionalWithIdenticalBranchesInspection, GroovyIfStatementWithIdenticalBranchesInspection)
  }

  void 'test two new expressions'() { doTest() }

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
