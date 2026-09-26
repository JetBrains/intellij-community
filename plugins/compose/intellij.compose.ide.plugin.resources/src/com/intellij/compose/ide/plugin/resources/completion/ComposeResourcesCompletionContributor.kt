// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.compose.ide.plugin.resources.completion

import com.intellij.codeInsight.completion.CompletionContributor
import com.intellij.codeInsight.completion.CompletionParameters
import com.intellij.codeInsight.completion.CompletionResult
import com.intellij.codeInsight.completion.CompletionResultSet
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.codeInsight.lookup.LookupElementDecorator
import com.intellij.codeInsight.lookup.LookupElementPresentation
import com.intellij.codeInsight.lookup.LookupElementRenderer
import com.intellij.compose.ide.plugin.resources.ResourceType
import com.intellij.compose.ide.plugin.resources.getComposeResourcesDir
import com.intellij.compose.ide.plugin.resources.getResourceItem
import com.intellij.compose.ide.plugin.resources.isComposeResClass
import com.intellij.compose.ide.plugin.resources.isComposeResourceProperty
import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.kotlin.idea.base.util.module
import org.jetbrains.kotlin.psi.KtDotQualifiedExpression
import org.jetbrains.kotlin.psi.KtNamedDeclaration
import org.jetbrains.kotlin.psi.KtProperty
import javax.swing.Icon

internal class ComposeResourcesCompletionContributor : CompletionContributor() {

  override fun fillCompletionVariants(parameters: CompletionParameters, resultSet: CompletionResultSet) {
    val resourceType = getComposeResourceType(parameters) ?: return
    val iconCache = ComposeResourcesGutterIconCache.getInstance(parameters.position.project)

    resultSet.runRemainingContributors(parameters) { completionResult ->
      val psiElement = completionResult.lookupElement.psiElement
      if (psiElement !is KtNamedDeclaration) {
        resultSet.passResult(completionResult)
        return@runRemainingContributors
      }

      if (psiElement !is KtProperty || !psiElement.isComposeResourceProperty) return@runRemainingContributors
      val result = if (resourceType == ResourceType.DRAWABLE) completionResult.decorateDrawable(psiElement, iconCache) else completionResult
      resultSet.passResult(result)
    }
  }
}

private fun getComposeResourceType(parameters: CompletionParameters): ResourceType? {
  val position = parameters.position
  val outerExpression = position.parent?.parent as? KtDotQualifiedExpression ?: return null
  val innerExpression = outerExpression.receiverExpression as? KtDotQualifiedExpression ?: return null
  if (!innerExpression.isComposeResClass) return null
  val accessorName = innerExpression.selectorExpression?.text ?: return null
  return ResourceType.entries.firstOrNull { it.accessorName == accessorName }
}

private fun CompletionResult.decorateDrawable(psiElement: KtProperty, iconCache: ComposeResourcesGutterIconCache): CompletionResult {
  val resourceFile = psiElement.resolveResourceFile() ?: return this
  val original = lookupElement
  val cachedIcon = iconCache.getIconIfCached(resourceFile)

  val decorated =
    if (cachedIcon != null) FastDrawableResourceLookupElement(original, cachedIcon)
    else SlowDrawableResourceLookupElement(original, resourceFile, iconCache)

  return withLookupElement(decorated)
}

private fun KtProperty.resolveResourceFile(): VirtualFile? {
  val module = module ?: return null
  val resourceItem = getResourceItem(this) ?: return null
  val composeResourcesDir = module.getComposeResourcesDir() ?: return null
  return resourceItem.paths.firstNotNullOfOrNull { path ->
    composeResourcesDir.findFileByRelativePath(path)
  }
}

private class FastDrawableResourceLookupElement(
  original: LookupElement,
  private val icon: Icon,
) : LookupElementDecorator<LookupElement>(original) {

  override fun renderElement(presentation: LookupElementPresentation) {
    super.renderElement(presentation)
    presentation.icon = icon
  }
}

private class SlowDrawableResourceLookupElement(
  original: LookupElement,
  private val file: VirtualFile,
  private val iconCache: ComposeResourcesGutterIconCache,
) : LookupElementDecorator<LookupElement>(original) {

  override fun getExpensiveRenderer(): LookupElementRenderer<out LookupElement> =
    object : LookupElementRenderer<SlowDrawableResourceLookupElement>() {
      override fun renderElement(element: SlowDrawableResourceLookupElement, presentation: LookupElementPresentation) {
        element.renderElement(presentation)
        val icon = element.iconCache.getIcon(element.file, ComposeResourcesGutterIconFactory::renderDrawableIcon)
        if (icon != null) presentation.icon = icon
      }
    }
}