// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.highlighting

import com.intellij.testFramework.LightProjectDescriptor
import groovy.transform.CompileStatic
import org.jetbrains.plugins.groovy.GroovyLightProjectDescriptor

@CompileStatic
class GroovyPre30HighlightingTest extends GrHighlightingTestBase {

  final LightProjectDescriptor projectDescriptor = GroovyLightProjectDescriptor.GROOVY_2_3
  final String basePath = super.basePath + 'pre30/'

  void 'test identity operators'() {
    fixture.testHighlighting testName + '.groovy'
  }

  void 'test elvis assignment'() {
    fixture.testHighlighting testName + '.groovy'
  }

  void 'test safe index access'() {
    fixture.testHighlighting testName + '.groovy'
  }

  void 'test negated in'() {
    fixture.testHighlighting testName + '.groovy'
  }

  void 'test negated instanceof'() {
    fixture.testHighlighting testName + '.groovy'
  }
}
