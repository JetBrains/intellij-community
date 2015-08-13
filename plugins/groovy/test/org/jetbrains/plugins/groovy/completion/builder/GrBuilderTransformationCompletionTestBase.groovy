/*
 * Copyright 2000-2015 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.plugins.groovy.completion.builder

import com.intellij.testFramework.LightProjectDescriptor
import groovy.transform.CompileStatic
import org.jetbrains.annotations.NotNull
import org.jetbrains.plugins.groovy.GroovyLightProjectDescriptor
import org.jetbrains.plugins.groovy.completion.CompletionResult
import org.jetbrains.plugins.groovy.completion.GroovyCompletionTestBase

import static com.intellij.codeInsight.completion.CompletionType.BASIC

@CompileStatic
abstract class GrBuilderTransformationCompletionTestBase extends GroovyCompletionTestBase {

  @Override
  @NotNull
  protected LightProjectDescriptor getProjectDescriptor() {
    return GroovyLightProjectDescriptor.GROOVY_2_3_9;
  }

  void doVariantableTest(String text, String... args) {
    doVariantableTest(text, CompletionResult.contain, args)
  }

  void doVariantableTest(String text, CompletionResult cr, String... args) {
    doVariantableTest(text, '', BASIC, cr, 1, args)
  }
}
