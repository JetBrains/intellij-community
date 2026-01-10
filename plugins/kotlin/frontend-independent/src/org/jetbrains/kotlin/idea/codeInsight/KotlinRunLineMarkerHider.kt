// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.codeInsight

import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.PossiblyDumbAware
import com.intellij.psi.PsiElement
import org.jetbrains.annotations.Contract

/**
 * An extension point to check whether the Kotlin run line marker should be hidden.
 */
interface KotlinRunLineMarkerHider : PossiblyDumbAware {

  @Contract(pure = true)
  fun shouldHideRunLineMarker(element: PsiElement): Boolean

  companion object {

    fun shouldHideRunLineMarker(element: PsiElement): Boolean {
      // This logic is similar to ApplicationRunLineMarkerHider
      val extensions: List<KotlinRunLineMarkerHider> = EXTENSION_POINT_NAME.extensionList
      val filteredExtensions: List<KotlinRunLineMarkerHider> = DumbService.getInstance(element.getProject()).filterByDumbAwareness(extensions)
      return extensions.size != filteredExtensions.size || extensions.any { it.shouldHideRunLineMarker(element) }
    }

    private val EXTENSION_POINT_NAME: ExtensionPointName<KotlinRunLineMarkerHider> =
        ExtensionPointName.create<KotlinRunLineMarkerHider>("org.jetbrains.kotlin.idea.codeInsight.lineMarkers.kotlinRunLineMarkerHider")
  }

}