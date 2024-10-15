// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.completion

import com.intellij.testFramework.LightProjectDescriptor
import groovy.transform.CompileStatic
import org.jetbrains.plugins.groovy.GroovyProjectDescriptors
import org.jetbrains.plugins.groovy.util.TestUtils

@CompileStatic
class GrLambdaCompletionTest extends GrFunctionalExpressionCompletionTest {
  final LightProjectDescriptor projectDescriptor = GroovyProjectDescriptors.GROOVY_3_0
  final String basePath = TestUtils.testDataPath + "groovy/completion/lambda"
}
