// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.junit2.properties

import com.intellij.lang.properties.psi.Property
import com.intellij.model.Pointer
import com.intellij.openapi.util.NlsSafe
import com.intellij.platform.backend.documentation.DocumentationResult
import com.intellij.platform.backend.documentation.DocumentationTarget
import com.intellij.platform.backend.documentation.PsiDocumentationTargetProvider
import com.intellij.platform.backend.presentation.TargetPresentation
import com.intellij.psi.PsiElement
import com.intellij.psi.createSmartPointer
import com.intellij.psi.util.parentOfType

/**
 * Provides quick documentation for `junit-platform.properties` keys described by library-provided metadata.
 */
internal class JUnitPlatformPropertyDocumentationTargetProvider : PsiDocumentationTargetProvider {
  override fun documentationTarget(element: PsiElement, originalElement: PsiElement?): DocumentationTarget? {
    val file = element.containingFile ?: return null
    if (file.name != JUNIT_PLATFORM_PROPERTIES_CONFIG) return null

    val key = element.parentOfType<Property>(withSelf = true)?.key ?: return null
    val descriptionHtml = getJUnitPlatformProperties(file)[key]?.html ?: return null

    return JUnitPlatformPropertyDocumentationTarget(element, key, descriptionHtml)
  }

  private class JUnitPlatformPropertyDocumentationTarget(
    private val element: PsiElement,
    private val key: @NlsSafe String,
    private val descriptionHtml: @NlsSafe String,
  ) : DocumentationTarget {
    override fun createPointer(): Pointer<out DocumentationTarget> {
      val pointer = element.createSmartPointer()
      val key = key
      val descriptionHtml = descriptionHtml
      return Pointer {
        val restored = pointer.dereference() ?: return@Pointer null
        JUnitPlatformPropertyDocumentationTarget(restored, key, descriptionHtml)
      }
    }

    override fun computePresentation(): TargetPresentation = TargetPresentation.builder(key).presentation()

    override fun computeDocumentation(): DocumentationResult = DocumentationResult.documentation(descriptionHtml)
  }
}