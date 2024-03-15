// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang

import groovy.transform.CompileStatic
import org.jetbrains.plugins.groovy.LightGroovyTestCase
import org.jetbrains.plugins.groovy.util.HighlightingTest

@CompileStatic
abstract class GroovyVersionBasedTest extends LightGroovyTestCase implements HighlightingTest {

  void 'test identity operators'() { fileHighlightingTest() }

  void 'test elvis assignment'() { fileHighlightingTest() }

  void 'test safe index access'() { fileHighlightingTest() }

  void 'test negated in'() { fileHighlightingTest() }

  void 'test negated instanceof'() { fileHighlightingTest() }

  void 'test method reference'() { fileHighlightingTest() }

  void 'test do while'() { fileHighlightingTest() }

  void 'test for'() { fileHighlightingTest() }

  void 'test try resources'() { fileHighlightingTest() }

  void 'test array initializers'() { fileHighlightingTest() }

  void 'test lambdas'() { fileHighlightingTest() }

  void 'test ambiguous code block'() { fileHighlightingTest() }

  void 'test type annotations'() { fileHighlightingTest() }

  void 'test application tuple initializer'() { fileHighlightingTest() }

  void 'test tuple multiple assignment'() { fileHighlightingTest() }
}
