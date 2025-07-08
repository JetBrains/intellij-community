package com.intellij.compose.ide.plugin.k2

import com.intellij.compose.ide.plugin.shared.COMPOSABLE_ANNOTATION_FQ_NAME
import com.intellij.compose.ide.plugin.shared.isFunctionExtractionInComposableControlFlow
import org.jetbrains.kotlin.idea.k2.refactoring.extractFunction.ExtractFunctionDescriptorModifier
import org.jetbrains.kotlin.idea.k2.refactoring.extractFunction.ExtractableCodeDescriptor

/**
 * Responsible for adding `@Composable` annotation to functions extracted from inside Composable control flow.
 *
 * For details see: [com.intellij.compose.ide.plugin.shared.isFunctionExtractionInComposableControlFlow]
 */
internal class K2ComposableAnnotationToExtractedFunctionAdder : ExtractFunctionDescriptorModifier {
  override fun modifyDescriptor(descriptor: ExtractableCodeDescriptor): ExtractableCodeDescriptor =
    if (composableAnnotationText !in descriptor.annotationsText && descriptor.extractionData.isFunctionExtractionInComposableControlFlow())
      descriptor.copy(renderedAnnotations = descriptor.renderedAnnotations + "$composableAnnotationText ")
    else descriptor
}

private val composableAnnotationText = "@${COMPOSABLE_ANNOTATION_FQ_NAME.asString()}"