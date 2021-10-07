// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.highlighting

import com.intellij.testFramework.LightProjectDescriptor
import groovy.transform.CompileStatic
import org.jetbrains.plugins.groovy.GroovyProjectDescriptors
import org.jetbrains.plugins.groovy.LightGroovyTestCase
import org.jetbrains.plugins.groovy.codeInspection.cs.GrPOJOInspection
import org.jetbrains.plugins.groovy.util.HighlightingTest

@CompileStatic
class GroovyRecordHighlightingTest extends LightGroovyTestCase implements HighlightingTest {

  final LightProjectDescriptor projectDescriptor = GroovyProjectDescriptors.GROOVY_4_0_REAL_JDK

  void 'test pojo without cs'() {
    highlightingTest '''
import groovy.transform.stc.POJO

<warning>@POJO</warning>
class A {}
''', GrPOJOInspection
  }
}
