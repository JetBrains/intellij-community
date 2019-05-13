// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.codeInspection.confusing

import com.intellij.codeInspection.LocalInspectionTool
import org.jetbrains.plugins.groovy.util.GroovyLatestTest
import org.jetbrains.plugins.groovy.util.HighlightingTest
import org.junit.Test

class UnnecessaryQualifiedReferenceInspectionTest extends GroovyLatestTest implements HighlightingTest {

  final Collection<Class<? extends LocalInspectionTool>> inspections = [UnnecessaryQualifiedReferenceInspection]

  @Test
  void 'attribute expression'() {
    highlightingTest '''\
class A { static foo }
A.@foo
A.@foo()
'''
  }
}
