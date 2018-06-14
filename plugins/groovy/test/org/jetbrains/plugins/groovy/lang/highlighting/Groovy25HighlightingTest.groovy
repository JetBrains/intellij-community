// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.highlighting

import com.intellij.testFramework.LightProjectDescriptor
import groovy.transform.CompileStatic
import org.jetbrains.plugins.groovy.GroovyProjectDescriptors
import org.jetbrains.plugins.groovy.LightGroovyTestCase
import org.jetbrains.plugins.groovy.util.HighlightingTest
import org.jetbrains.plugins.groovy.util.TestUtils

@CompileStatic
class Groovy25HighlightingTest extends LightGroovyTestCase implements HighlightingTest {

  final LightProjectDescriptor projectDescriptor = GroovyProjectDescriptors.GROOVY_2_5
  final String basePath = TestUtils.testDataPath + 'highlighting/v25/'

  void 'test duplicating named params'() { highlightingTest() }
  void 'test duplicating named params with setter'() { highlightingTest() }
  void 'test two identical named delegates'() { highlightingTest() }
  void 'test duplicating named delegates with usual parameter'() { highlightingTest() }

  void 'test named params type check'() { highlightingTest() }
  void 'test named params type check with setter'() { highlightingTest() }
}