// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.highlighting

import com.intellij.testFramework.LightProjectDescriptor
import groovy.transform.CompileStatic
import org.jetbrains.plugins.groovy.GroovyProjectDescriptors

@CompileStatic
class GrHighlightingAttributesTest extends GrHighlightingTestBase {

  final LightProjectDescriptor projectDescriptor = GroovyProjectDescriptors.GROOVY_LATEST

  void 'test highlighting attributes'() {
    fixture.testHighlighting false, true, false, "highlightingAttributes/test.groovy"
  }

  void 'test todo highlighting'() {
    fixture.testHighlighting false, true, false, "highlightingAttributes/todo.groovy"
  }
}
