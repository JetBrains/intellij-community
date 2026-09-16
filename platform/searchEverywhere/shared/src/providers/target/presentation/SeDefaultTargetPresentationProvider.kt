// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.searchEverywhere.providers.target.presentation

import com.intellij.ide.actions.searcheverywhere.PsiItemWithSimilarity
import com.intellij.ide.util.PSIRenderingUtils
import com.intellij.ide.util.PsiElementRenderingInfo
import com.intellij.navigation.ItemPresentation
import com.intellij.navigation.NavigationItem
import com.intellij.navigation.PsiElementNavigationItem
import com.intellij.openapi.application.readAction
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.util.Iconable
import com.intellij.platform.backend.navigation.NavigationTarget
import com.intellij.platform.backend.presentation.TargetPresentation
import com.intellij.psi.PsiElement
import com.intellij.psi.presentation.java.SymbolPresentationUtil
import com.intellij.util.ui.EmptyIcon
import javax.swing.Icon

/**
 * The fallback [SeTargetPresentationProvider]. It applies to every item, so it carries `order="last"`.
 *
 * This is the port of `PSIPresentationBgRendererWrapper.calcPresentation` and its family. The old code
 * reached the PSI presentation through `SearchEverywherePsiRenderer`, which is a Swing component. This
 * class calls [PsiElementRenderingInfo.targetPresentation] instead. That method computes the same
 * [TargetPresentation] and builds no component.
 */
internal class SeDefaultTargetPresentationProvider : SeTargetPresentationProvider {
  override fun appliesTo(item: Any): Boolean = true

  override suspend fun getPresentation(item: Any): TargetPresentation = readAction {
    computePresentation(item)
  }

  /** Runs in one read action, so the recursion opens no nested read action. */
  private fun computePresentation(item: Any): TargetPresentation = when (item) {
    // The similarity wrapper carries only a score. The presentation comes from the wrapped item.
    is PsiItemWithSimilarity<*> -> computePresentation(item.value)
    is NavigationTarget -> item.computePresentation()
    is PsiElement -> psiPresentation(item)
    is PsiElementNavigationItem -> item.targetElement?.let { psiPresentation(it) } ?: fallbackPresentation(item)
    // The old code called Objects.requireNonNull here. A null presentation now falls back instead.
    is NavigationItem -> item.presentation?.let { convertPresentation(it) } ?: fallbackPresentation(item)
    is ItemPresentation -> convertPresentation(item)
    else -> fallbackPresentation(item)
  }

  private fun psiPresentation(element: PsiElement): TargetPresentation =
    PsiElementRenderingInfo.targetPresentation(element, SeTargetRenderingInfo)

  private fun convertPresentation(presentation: ItemPresentation): TargetPresentation =
    TargetPresentation.builder(presentation.presentableText ?: "")
      .icon(presentation.getIcon(true))
      .locationText(presentation.locationString)
      .presentation()

  private fun fallbackPresentation(item: Any): TargetPresentation {
    LOG.error("A Search Everywhere item must be a PSI item, or must carry an ItemPresentation. " +
              "The item of class ${item.javaClass.name} carries neither.")
    @Suppress("HardCodedStringLiteral")
    return TargetPresentation.builder(item.toString()).icon(EmptyIcon.ICON_16).presentation()
  }

  private companion object {
    private val LOG = logger<SeDefaultTargetPresentationProvider>()
  }
}

/**
 * The Swing-free equivalent of the three `SearchEverywherePsiRenderer` overrides that
 * `PsiElementListCellRenderer.computePresentation` reads.
 */
private object SeTargetRenderingInfo : PsiElementRenderingInfo<PsiElement> {
  /** Mirrors `SearchEverywherePsiRenderer.getIconFlags`. */
  override fun getIcon(element: PsiElement): Icon? = element.getIcon(Iconable.ICON_FLAG_READ_STATUS)

  /** Mirrors `SearchEverywherePsiRenderer.getElementText`. */
  override fun getPresentableText(element: PsiElement): String = PSIRenderingUtils.getPSIElementText(element)

  /**
   * Mirrors `SearchEverywherePsiRenderer.getContainerTextForLeftComponent` with no `FontMetrics`.
   *
   * The old method also shortened the text to the width of the list. That step needs a font, so it
   * belongs to the UI and stays out of this computation.
   */
  override fun getContainerText(element: PsiElement): String? {
    val presentablePath = PSIRenderingUtils.extractPresentablePath(element)
    val text = presentablePath ?: SymbolPresentationUtil.getSymbolContainerText(element)
    if (text == null || text == getPresentableText(element)) return null
    return PSIRenderingUtils.normalizePsiElementContainerText(element, text, presentablePath)
  }
}
