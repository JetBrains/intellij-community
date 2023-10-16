// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.devkit.workspaceModel

import com.intellij.codeInspection.*
import com.intellij.openapi.project.Project
import org.jetbrains.annotations.VisibleForTesting
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtProperty
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.psi.KtVisitorVoid

@VisibleForTesting
class WorkspaceEntityMutableFieldInspection: LocalInspectionTool() {
  override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean) = object : KtVisitorVoid() {
    override fun visitClass(klass: KtClass) {
      if (!klass.isWorkspaceEntity()) return
      if (klass.name == "Builder") return
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