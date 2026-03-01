// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.devkit.workspaceModel.inspections

import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.codeInspection.LocalQuickFix
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.devkit.workspaceModel.DevKitWorkspaceModelBundle
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.IntellijInternalApi
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.VisibleForTesting
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtProperty
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.psi.KtVisitorVoid

@VisibleForTesting
@IntellijInternalApi
@ApiStatus.Internal
class WorkspaceEntityMutableFieldInspection: LocalInspectionTool() {
  override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): KtVisitorVoid = object : KtVisitorVoid() {
    override fun visitClass(klass: KtClass) {
      if (!klass.isWorkspaceEntityDeclaration()) return
      klass.getProperties().forEach { property ->
        if (property.isVar) {
          holder.registerProblem(property, DevKitWorkspaceModelBundle.message("inspection.workspace.mutable.field.display.name"),
                                 ProblemHighlightType.GENERIC_ERROR, MakeFieldImmutableFix())
        }
      }
    }
  }
}

private class MakeFieldImmutableFix : LocalQuickFix {
  override fun getName() = DevKitWorkspaceModelBundle.message("inspection.workspace.msg.change.field.to.val")
  override fun getFamilyName() = name
  override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
    val psiElement = descriptor.psiElement
    if (!psiElement.isValid) return
    if (psiElement !is KtProperty) return

    val factory = KtPsiFactory(project)
    psiElement.valOrVarKeyword.replace(factory.createValKeyword())
  }
}