// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.compose.ide.plugin.resources.intentions

import com.intellij.codeInspection.LocalQuickFix
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.compose.ide.plugin.shared.COMPOSE_PAINTER_RESOURCE_FQ_NAME
import com.intellij.compose.ide.plugin.shared.COMPOSE_PAINTER_RESOURCE_NAME
import com.intellij.compose.ide.plugin.shared.ComposeIdeBundle
import com.intellij.openapi.project.Project
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.resolve.ImportPath

internal class ComposeResourcesImageResourceQuickFix : LocalQuickFix {
  override fun getFamilyName(): String = ComposeIdeBundle.message("compose.inspection.image.resource.fix.name")

  override fun getName(): String = getFamilyName()

  override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
    val imageResourceCall = descriptor.imageResourceCall() ?: return
    val calleeExpression = imageResourceCall.calleeExpression ?: return
    val ktFile = imageResourceCall.containingKtFile
    val factory = KtPsiFactory.contextual(imageResourceCall)

    calleeExpression.replace(factory.createExpression(COMPOSE_PAINTER_RESOURCE_NAME.asString()))
    ktFile.addImportIfMissing(COMPOSE_PAINTER_RESOURCE_FQ_NAME.asString())
  }
}

private fun KtFile.addImportIfMissing(fqName: String) {
  val importPath = ImportPath.fromString(fqName)
  val importList = importList ?: return
  if (importList.imports.any { it.importPath == importPath }) return
  importList.add(KtPsiFactory(project).createImportDirective(importPath))
}

private fun ProblemDescriptor.imageResourceCall(): KtCallExpression? =
  psiElement as? KtCallExpression ?: psiElement.parent as? KtCallExpression
