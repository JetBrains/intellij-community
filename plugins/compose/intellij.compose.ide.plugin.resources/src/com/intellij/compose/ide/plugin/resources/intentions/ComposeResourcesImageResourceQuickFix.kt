// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.compose.ide.plugin.resources.intentions

import com.intellij.compose.ide.plugin.shared.COMPOSE_FILTER_QUALITY_FQ_NAME
import com.intellij.compose.ide.plugin.shared.COMPOSE_IMAGE_PAINTER_PARAMETER_NAME
import com.intellij.compose.ide.plugin.shared.COMPOSE_IMAGE_RESOURCE_CALLABLE_ID
import com.intellij.compose.ide.plugin.shared.COMPOSE_PAINTER_RESOURCE_FQ_NAME
import com.intellij.compose.ide.plugin.shared.COMPOSE_PAINTER_RESOURCE_NAME
import com.intellij.compose.ide.plugin.shared.ComposeIdeBundle
import com.intellij.modcommand.ModPsiUpdater
import com.intellij.modcommand.PsiUpdateModCommandQuickFix
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.createSmartPointer
import org.jetbrains.kotlin.idea.base.codeInsight.KotlinOptimizeImportsFacility
import org.jetbrains.kotlin.idea.base.psi.deleteValueArgument
import org.jetbrains.kotlin.idea.base.psi.imports.addImport
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtDotQualifiedExpression
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.psi.KtValueArgument
import org.jetbrains.kotlin.psi.KtValueArgumentList
import org.jetbrains.kotlin.psi.psiUtil.getQualifiedExpressionForSelector
import org.jetbrains.kotlin.psi.psiUtil.getQualifiedExpressionForSelectorOrThis

internal class ComposeResourcesImageResourceQuickFix(filterQualityArgument: KtValueArgument?) : PsiUpdateModCommandQuickFix() {
  private val filterQualityArgumentPointer = filterQualityArgument?.createSmartPointer()

  override fun getFamilyName(): String = ComposeIdeBundle.message("compose.inspection.image.resource.fix.name")

  override fun applyFix(project: Project, element: PsiElement, updater: ModPsiUpdater) {
    val call = element.imageResourceCall() ?: return
    val bitmapArgument = call.getQualifiedExpressionForSelectorOrThis().parent as? KtValueArgument ?: return
    val filterQualityArgument = filterQualityArgumentPointer?.element?.let(updater::getWritable)
    val factory = KtPsiFactory.contextual(call)

    // Rename the named `bitmap` argument to `painter`
    val painterName = factory.createSimpleName(COMPOSE_IMAGE_PAINTER_PARAMETER_NAME.asString())
    bitmapArgument.getArgumentName()?.referenceExpression?.replace(painterName)
    // The painter overload has no `filterQuality` parameter
    filterQualityArgument?.let { (it.parent as? KtValueArgumentList)?.deleteValueArgument(it) }
    call.replaceWithPainterResource(factory)

    // Remove only the imports that this fix made unused
    val importsToRemove = listOfNotNull(
      COMPOSE_IMAGE_RESOURCE_CALLABLE_ID.asSingleFqName(),
      COMPOSE_FILTER_QUALITY_FQ_NAME.takeIf { filterQualityArgument != null },
    )
    call.containingKtFile.removeImportsIfUnused(importsToRemove)
  }
}

private fun PsiElement.imageResourceCall(): KtCallExpression? =
  when (this) {
    is KtDotQualifiedExpression -> selectorExpression as? KtCallExpression
    else -> parent as? KtCallExpression
  }

private fun KtCallExpression.replaceWithPainterResource(factory: KtPsiFactory) {
  calleeExpression?.replace(factory.createSimpleName(COMPOSE_PAINTER_RESOURCE_NAME.asString())) ?: return
  if (getQualifiedExpressionForSelector() == null) containingKtFile.addImport(COMPOSE_PAINTER_RESOURCE_FQ_NAME)
}

private fun KtFile.removeImportsIfUnused(fqNames: Collection<FqName>) {
  KotlinOptimizeImportsFacility.getInstance()
    .analyzeImports(this)
    ?.unusedImports
    ?.filter { it.importedFqName in fqNames }
    ?.forEach { it.delete() }
}
