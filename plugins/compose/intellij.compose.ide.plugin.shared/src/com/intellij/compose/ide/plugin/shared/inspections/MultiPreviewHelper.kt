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
package com.intellij.compose.ide.plugin.shared.inspections

import com.intellij.compose.ide.plugin.shared.COMPOSABLE_ANNOTATION_FQ_NAME
import com.intellij.compose.ide.plugin.shared.JETPACK_PREVIEW_FQ_NAME
import com.intellij.compose.ide.plugin.shared.MULTIPLATFORM_PREVIEW_FQ_NAME
import com.intellij.openapi.diagnostic.Logger
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiModifierListOwner
import com.intellij.psi.util.parentOfType
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import com.intellij.util.concurrency.annotations.RequiresReadLock
import org.jetbrains.kotlin.psi.KtAnnotationEntry
import org.jetbrains.kotlin.utils.ifEmpty
import org.jetbrains.uast.UAnnotation
import org.jetbrains.uast.UElement
import org.jetbrains.uast.UMethod
import org.jetbrains.uast.getParentOfType
import org.jetbrains.uast.toUElement
import org.jetbrains.uast.toUElementOfType
import org.jetbrains.uast.tryResolve

/**
 * Checks if the given annotation is actually a multi-preview annotation. Example:
 * ```
 * @Preview
 * annotation class MyMultiPreview
 * ```
 * The abovementioned `MyMultiPreview` annotation is a valid annotation to mark previews:
 * ```
 * @Composable
 * @MyMultiPreview // this function should be recognized as a preview by the IDE.
 * fun MyViewPreview() { ... }
 * ```
 * This implementation is based on the original `com.android.tools.idea.compose.preview.isMultiPreviewAnnotation` from Android Design plugin,
 * but it cuts a lot of non-important details and is more performant, because:
 * - It eliminated the need to run [com.intellij.openapi.progress.runBlockingCancellable] inside;
 * - Removes unnecessary [com.intellij.openapi.application.readAction] calls;
 */
@RequiresReadLock
@RequiresBackgroundThread
internal fun KtAnnotationEntry.isMultiPreview(): Boolean = (toUElement() as? UAnnotation)?.hasPreviewNodes() == true

@RequiresReadLock
private fun UAnnotation.hasPreviewNodes(): Boolean {
  getContainingComposableUMethod() ?: return false
  return findAllPreviewAnnotationsInGraph().isNotEmpty()
}

@RequiresReadLock
private fun UAnnotation.getContainingComposableUMethod(): UMethod? {
  val uMethod = getParentOfType(UMethod::class.java)
                ?: javaPsi?.parentOfType<PsiMethod>()?.toUElement(UMethod::class.java)
                ?: return null
  return if (uMethod.uAnnotations.any { annotation -> COMPOSABLE_ANNOTATION_FQ_NAME.asString() == annotation.qualifiedName }) {
    uMethod
  } else {
    null
  }
}

private fun UElement.findAllPreviewAnnotationsInGraph(): List<UElement> {
  return traverseAnnotations(
    listOf(this),
    annotationFilter = { (it.isPsiValid && it.couldBeMultiPreviewAnnotation()) || it.isPreviewAnnotation() },
    isLeafAnnotation = { it.isPreviewAnnotation() },
  )
}

@RequiresReadLock
private fun UAnnotation.isPreviewAnnotation(): Boolean =
  MULTIPLATFORM_PREVIEW_FQ_NAME.asString() == qualifiedName ||
  JETPACK_PREVIEW_FQ_NAME.asString() == qualifiedName

@RequiresReadLock
private fun UAnnotation.couldBeMultiPreviewAnnotation(): Boolean {
  return this.qualifiedName
    ?.let { fqcn ->
      when {
        fqcn.startsWith("androidx.") || fqcn.startsWith("org.jetbrains.compose.") ->
          fqcn.contains(".preview.")
        else ->
          NON_MULTIPREVIEW_PREFIXES.none { fqcn.startsWith(it) }
      }
    } == true
}

private val NON_MULTIPREVIEW_PREFIXES = listOf("android.", "kotlin.", "kotlinx.", "java.")

@RequiresReadLock
private fun traverseAnnotations(
  sourceElements: List<UElement>,
  annotationFilter: (UAnnotation) -> Boolean,
  isLeafAnnotation: (UAnnotation) -> Boolean,
): List<UElement> {
  val visitedAnnotationClasses = mutableMapOf<String, UElement>()
  return sourceElements.flatMap { iterativeDfs(it, visitedAnnotationClasses, annotationFilter, isLeafAnnotation) }
}

/** Iterative DFS implementation, to avoid stack overflow-related problems for big graphs. */
@RequiresReadLock
private fun iterativeDfs(
  rootElement: UElement,
  visitedAnnotationClasses: MutableMap<String, UElement>,
  annotationFilter: (UAnnotation) -> Boolean,
  isLeafAnnotation: (UAnnotation) -> Boolean,
): List<UElement> {
  class DfsNode(val element: UElement) {
    var isProcessed: Boolean = false

    fun process() {
      isProcessed = true
    }
  }

  val result = mutableListOf<UElement>()
  val stack = mutableListOf(DfsNode(rootElement))
  while (stack.isNotEmpty()) {
    val node = stack.removeAt(stack.size - 1)
    if (node.isProcessed) {
      node.element.takeIf { it is UAnnotation && it.isPreviewAnnotation() }?.let { result.add(it) }
      continue
    }

    val annotationName = (node.element as? UAnnotation)?.let {
      // Log any unexpected null name as this could cause problems in the DFS.
      it.qualifiedName.also { fqn -> if (fqn == null) logger.warn("Failed to resolve annotation qualified name") }
    }

    // Skip already visited annotations
    if (annotationName != null && visitedAnnotationClasses.containsKey(annotationName)) {
      continue
    }

    // Process the node and schedule its UP for after its children
    node.process()
    stack.add(node)

    // Leaf annotations don't have children and could be visited multiple times, so they should
    // not even be marked as visited.
    if ((node.element as? UAnnotation)?.let { isLeafAnnotation(it) } == true) {
      continue
    }

    // Mark the current annotation as visited
    annotationName?.let { visitedAnnotationClasses[it] = node.element }

    // Non-leaf annotations go down to its children annotations
    val annotations = node.element.getUAnnotations()
    annotations
      .filter { annotationFilter(it) }
      .reversed() // reversed to keep the correct order in the stack
      .forEach { annotation ->
        stack.add(DfsNode(element = annotation))
      }
  }

  return result
}

@RequiresReadLock
private fun UElement.getUAnnotations(): List<UAnnotation> {
  val annotations = (this as? UMethod)?.uAnnotations ?: (this.tryResolve() as? PsiModifierListOwner)?.annotations?.mapNotNull {
    it.toUElementOfType() as? UAnnotation
  } ?: emptyList()
  return annotations.flatMap { annotation ->
    annotation.extractFromContainer().ifEmpty { listOf(annotation) }
  }
}

@RequiresReadLock
private fun UAnnotation.extractFromContainer(): List<UAnnotation> =
  findDeclaredAttributeValue(null)?.sourcePsi?.children?.mapNotNull {
    it.toUElement() as? UAnnotation
  } ?: emptyList()

private val logger = Logger.getInstance("MultiPreviewHelper")
