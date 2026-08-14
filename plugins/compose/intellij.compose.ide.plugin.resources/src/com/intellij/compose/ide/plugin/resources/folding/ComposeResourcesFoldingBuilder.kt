// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.compose.ide.plugin.resources.folding

import com.intellij.compose.ide.plugin.resources.ResourceType
import com.intellij.compose.ide.plugin.resources.getResourceItem
import com.intellij.lang.ASTNode
import com.intellij.lang.folding.FoldingBuilderEx
import com.intellij.lang.folding.FoldingDescriptor
import com.intellij.openapi.editor.Document
import com.intellij.openapi.util.text.StringUtil
import com.intellij.psi.PsiElement
import com.intellij.psi.util.childrenSequence
import com.intellij.psi.util.parentOfType
import com.intellij.psi.xml.XmlTag
import org.jetbrains.kotlin.idea.KotlinLanguage
import org.jetbrains.kotlin.psi.KtDotQualifiedExpression
import org.jetbrains.kotlin.psi.KtImportDirective
import org.jetbrains.kotlin.psi.KtSimpleNameExpression
import org.jetbrains.kotlin.psi.KtTreeVisitorVoid

private const val FOLD_MAX_LENGTH = 60

internal class ComposeResourcesFoldingBuilder : FoldingBuilderEx() {

  override fun isCollapsedByDefault(node: ASTNode): Boolean = true

  override fun getPlaceholderText(node: ASTNode): String? =
    null

  override fun buildFoldRegions(root: PsiElement, document: Document, quick: Boolean): Array<out FoldingDescriptor?> {
    if (root.language !is KotlinLanguage || quick) return emptyArray()

    val descriptors = mutableListOf<FoldingDescriptor>()

    root.accept(object : KtTreeVisitorVoid() {
      override fun visitSimpleNameExpression(expression: KtSimpleNameExpression) {
        expression.getFoldingDescriptor()?.let(descriptors::add)
        super.visitSimpleNameExpression(expression)
      }
    })

    return descriptors.toTypedArray()
  }

}

private fun KtSimpleNameExpression.getFoldingDescriptor(): FoldingDescriptor? {
  if (parentOfType<KtImportDirective>() != null) return null

  val resourceItem = getResourceItem(this) ?: return null
  if (!resourceItem.type.isStringType) return null
  val resourcePsiElements = resourceItem.getPsiElements()

  val placeholderText = getResourcePlaceholderText(resourcePsiElements) ?: return null

  val element = parent as? KtDotQualifiedExpression ?: this
  val dependencies = buildSet {
    add(this@getFoldingDescriptor)
    addAll(resourcePsiElements)
  }
  return FoldingDescriptor(element.node, element.textRange, null, placeholderText, true, dependencies)
}

private fun getResourcePlaceholderText(psiElements: List<PsiElement>): String? {
  for (element in psiElements) {
    if (isValuesDirectory(element)) {
      extractPlaceholderText(element)?.let { return it }
    }
  }

  for (element in psiElements) {
    if (!isValuesDirectory(element)) {
      extractPlaceholderText(element)?.let { return it }
    }
  }

  return null
}

private fun isValuesDirectory(element: PsiElement): Boolean {
  return element.containingFile?.parent?.name == "values"
}

private fun extractPlaceholderText(psiElement: PsiElement): String? {
  val xmlTag = psiElement.parent?.parent as? XmlTag ?: return null

  val rawText = when (xmlTag.name) {
                  ResourceType.STRING.typeName -> xmlTag.value.text
                  ResourceType.PLURAL_STRING.typeName -> xmlTag.subTags.firstOrNull()?.value?.text
                  else -> null
                } ?: return null

  val trimmed = rawText.trim()
  if (trimmed.isEmpty()) return null

  val shortened = StringUtil.shortenTextWithEllipsis(trimmed, FOLD_MAX_LENGTH - 2, 0)
  return "\"$shortened\""
}
