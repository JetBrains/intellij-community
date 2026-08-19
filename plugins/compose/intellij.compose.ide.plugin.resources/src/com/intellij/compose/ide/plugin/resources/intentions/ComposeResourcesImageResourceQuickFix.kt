// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.compose.ide.plugin.resources.intentions

import com.intellij.codeInspection.LocalQuickFix
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.compose.ide.plugin.shared.COMPOSE_IMAGE_RESOURCE_CALLABLE_ID
import com.intellij.compose.ide.plugin.shared.COMPOSE_PAINTER_RESOURCE_FQ_NAME
import com.intellij.compose.ide.plugin.shared.COMPOSE_PAINTER_RESOURCE_NAME
import com.intellij.compose.ide.plugin.shared.ComposeIdeBundle
import com.intellij.openapi.project.Project
import org.jetbrains.kotlin.idea.base.codeInsight.KotlinOptimizeImportsFacility
import org.jetbrains.kotlin.idea.base.psi.imports.addImport
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtDotQualifiedExpression
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.psi.psiUtil.getQualifiedExpressionForSelector

internal class ComposeResourcesImageResourceQuickFix : LocalQuickFix {
  override fun getFamilyName(): String = ComposeIdeBundle.message("compose.inspection.image.resource.fix.name")

  override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
    val imageResourceCall = descriptor.imageResourceCall() ?: return
    val calleeExpression = imageResourceCall.calleeExpression ?: return
    val ktFile = imageResourceCall.containingKtFile
    val isFullyQualified = imageResourceCall.isFullyQualifiedCall()
    val factory = KtPsiFactory.contextual(imageResourceCall)

    calleeExpression.replace(factory.createExpression(COMPOSE_PAINTER_RESOURCE_NAME.asString()))
    if (!isFullyQualified) ktFile.addImport(FqName(COMPOSE_PAINTER_RESOURCE_FQ_NAME.asString()))
    ktFile.removeImageResourceImportIfUnused()
  }
}

private fun KtCallExpression.isFullyQualifiedCall(): Boolean =
  getQualifiedExpressionForSelector() != null

private fun KtFile.removeImageResourceImportIfUnused() {
  val imageResourceFqName = COMPOSE_IMAGE_RESOURCE_CALLABLE_ID.asSingleFqName()
  KotlinOptimizeImportsFacility.getInstance()
    .analyzeImports(this)
    ?.unusedImports
    ?.find { it.importedFqName == imageResourceFqName }
    ?.delete()
}

private fun ProblemDescriptor.imageResourceCall(): KtCallExpression? =
  when (val element = psiElement) {
    is KtCallExpression -> element
    is KtDotQualifiedExpression -> element.selectorExpression as? KtCallExpression
    else -> element.parent as? KtCallExpression
  }
