// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.codeInspection.bugs

import com.intellij.testFramework.LightProjectDescriptor
import groovy.transform.CompileStatic
import org.jetbrains.plugins.groovy.GroovyProjectDescriptors
import org.jetbrains.plugins.groovy.lang.highlighting.GrHighlightingTestBase

@CompileStatic
class GrEqualsBetweenInconvertibleTypesInspectionTest extends GrHighlightingTestBase {

  final String basePath = super.basePath + 'bugs/'
  final LightProjectDescriptor projectDescriptor = GroovyProjectDescriptors.GROOVY_3_0

  void 'test equals between inconvertible types'() {
    fixture.enableInspections GrEqualsBetweenInconvertibleTypesInspection
    fixture.testHighlighting "${testName}.groovy"
  }
}
