// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.completion.builder

import com.intellij.testFramework.LightProjectDescriptor
import groovy.transform.CompileStatic
import org.jetbrains.annotations.NotNull
import org.jetbrains.plugins.groovy.GroovyProjectDescriptors
import org.jetbrains.plugins.groovy.completion.CompletionResult
import org.jetbrains.plugins.groovy.completion.GroovyCompletionTestBase

import static com.intellij.codeInsight.completion.CompletionType.BASIC

@CompileStatic
abstract class GrBuilderTransformationCompletionTestBase extends GroovyCompletionTestBase {

  @Override
  @NotNull
  protected LightProjectDescriptor getProjectDescriptor() {
    return GroovyProjectDescriptors.GROOVY_LATEST
  }

  void doCompletionTest(String text, String... args) {
    doCompletionTest(text, CompletionResult.contain, args)
  }

  void doCompletionTest(String text, CompletionResult cr, String... args) {
    doVariantableTest(text, '', BASIC, cr, 1, args)
  }
}
