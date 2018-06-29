// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang

import groovy.transform.CompileStatic
import org.jetbrains.plugins.groovy.LightGroovyTestCase
import org.jetbrains.plugins.groovy.util.HighlightingTest

@CompileStatic
abstract class GroovyVersionBasedTest extends LightGroovyTestCase implements HighlightingTest {

  void 'test identity operators'() { highlightingTest() }

  void 'test elvis assignment'() { highlightingTest() }

  void 'test safe index access'() { highlightingTest() }

  void 'test negated in'() { highlightingTest() }

  void 'test negated instanceof'() { highlightingTest() }

  void 'test method reference'() { highlightingTest() }

  void 'test do while'() { highlightingTest() }

  void 'test try resources'() { highlightingTest() }
}
