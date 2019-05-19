// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.highlighting

import com.intellij.testFramework.LightProjectDescriptor
import groovy.transform.CompileStatic
import org.jetbrains.plugins.groovy.GroovyProjectDescriptors
import org.jetbrains.plugins.groovy.lang.GroovyVersionBasedTest
import org.jetbrains.plugins.groovy.util.TestUtils

@CompileStatic
class GroovyPre30HighlightingTest extends GroovyVersionBasedTest {

  final LightProjectDescriptor projectDescriptor = GroovyProjectDescriptors.GROOVY_2_3
  final String basePath = TestUtils.testDataPath + 'highlighting/pre30/'
}
