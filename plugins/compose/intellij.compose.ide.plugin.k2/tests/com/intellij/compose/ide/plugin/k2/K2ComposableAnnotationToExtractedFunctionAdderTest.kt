/*
 * Copyright (C) 2020 The Android Open Source Project
 * Modified 2025 by JetBrains s.r.o.
 * Copyright (C) 2025 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.compose.ide.plugin.k2

import com.intellij.compose.ide.plugin.shared.ComposableAnnotationToExtractedFunctionAdderTest
import com.intellij.openapi.application.Application
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.testFramework.fixtures.JavaCodeInsightTestFixture
import org.jetbrains.kotlin.analysis.api.permissions.KaAllowAnalysisOnEdt
import org.jetbrains.kotlin.analysis.api.permissions.allowAnalysisOnEdt
import org.jetbrains.kotlin.idea.base.plugin.KotlinPluginMode
import org.jetbrains.kotlin.idea.k2.refactoring.extractFunction.ExtractableCodeDescriptor
import org.jetbrains.kotlin.idea.k2.refactoring.extractFunction.ExtractableCodeDescriptorWithConflicts
import org.jetbrains.kotlin.idea.k2.refactoring.extractFunction.ExtractionGeneratorConfiguration
import org.jetbrains.kotlin.idea.k2.refactoring.extractFunction.ExtractionResult
import org.jetbrains.kotlin.idea.k2.refactoring.extractFunction.KotlinFirExtractFunctionHandler
import org.jetbrains.kotlin.idea.k2.refactoring.introduce.extractionEngine.ExtractionEngineHelper
import org.jetbrains.kotlin.idea.k2.refactoring.introduceConstant.INTRODUCE_CONSTANT
import org.jetbrains.kotlin.idea.k2.refactoring.introduceConstant.KotlinIntroduceConstantHandler
import org.jetbrains.kotlin.idea.refactoring.introduce.extractFunction.EXTRACT_FUNCTION
import org.jetbrains.kotlin.idea.refactoring.introduce.extractionEngine.ExtractionGeneratorOptions
import org.jetbrains.kotlin.name.FqName
import java.util.concurrent.Semaphore

class K2ComposableAnnotationToExtractedFunctionAdderTest : ComposableAnnotationToExtractedFunctionAdderTest() {
  override val pluginMode: KotlinPluginMode get() = KotlinPluginMode.K2

  override fun JavaCodeInsightTestFixture.invokeExtractFunctionIn(application: Application, existingAnnotationFqNames: List<FqName>) {
    val helper = ExtractionHelper(existingAnnotationFqNames)
    application.invokeAndWait {
      KotlinFirExtractFunctionHandler(helper = helper)
        .invoke(this.project, this.editor, this.file!!, null)
    }
    helper.waitUntilFinished()
  }

  override fun JavaCodeInsightTestFixture.invokeExtractConstantIn(application: Application) {
    val helper = InteractiveExtractionHelper()
    application.invokeAndWait {
      KotlinIntroduceConstantHandler(helper = helper)
        .invoke(this.project, this.editor, this.file!!, null)
    }
    helper.waitUntilFinished()
  }

}

// Following 2 are heavily inspired/copied from AOSP
private class ExtractionHelper(private val existingAnnotationFqNames: List<FqName>) : ExtractionEngineHelper(EXTRACT_FUNCTION) {
  private val finishedSemaphore = Semaphore(0)

  fun waitUntilFinished() {
    finishedSemaphore.acquire()
  }

  @OptIn(KaAllowAnalysisOnEdt::class)
  override fun configureAndRun(
    project: Project,
    editor: Editor,
    descriptorWithConflicts: ExtractableCodeDescriptorWithConflicts,
    onFinish: (ExtractionResult) -> Unit,
  ) {
    // `descriptorWithConflicts.descriptor.copy(..)` runs the constructor of
    // ExtractableCodeDescriptor
    // that calls AA for return type check. Since the copied one has the exactly same type as the
    // initial descriptor, if we only copy the boolean value, we do not need `allowAnalysisOnEdt`.
    // To do so, we have to update `ExtractableCodeDescriptor`.
    allowAnalysisOnEdt {
      val newDescriptor =
        descriptorWithConflicts.descriptor.copy(
          suggestedNames = listOf("newFunction"),
          renderedAnnotations = existingAnnotationFqNames.map { "@${it.asString()}" } +
                                descriptorWithConflicts.descriptor.renderedAnnotations
        )
      doRefactor(
        ExtractionGeneratorConfiguration(newDescriptor, ExtractionGeneratorOptions.DEFAULT)
      ) { er: ExtractionResult ->
        onFinish(er)
        finishedSemaphore.release()
      }
    }
  }
}

private class InteractiveExtractionHelper : ExtractionEngineHelper(INTRODUCE_CONSTANT) {
  private val finishedSemaphore = Semaphore(0)

  fun waitUntilFinished() {
    finishedSemaphore.acquire()
  }

  override fun validate(
    descriptor: ExtractableCodeDescriptor
  ): ExtractableCodeDescriptorWithConflicts =
    KotlinIntroduceConstantHandler.InteractiveExtractionHelper.validate(descriptor)

  override fun configureAndRun(
    project: Project,
    editor: Editor,
    descriptorWithConflicts: ExtractableCodeDescriptorWithConflicts,
    onFinish: (ExtractionResult) -> Unit,
  ) {
    KotlinIntroduceConstantHandler.InteractiveExtractionHelper.configureAndRun(
      project,
      editor,
      descriptorWithConflicts,
    ) { er: ExtractionResult ->
      onFinish(er)
      finishedSemaphore.release()
    }
  }
}