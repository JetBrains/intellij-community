// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.devkit.workspaceModel.inspections

import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.devkit.workspaceModel.DevKitWorkspaceModelBundle
import com.intellij.openapi.util.IntellijInternalApi
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.VisibleForTesting
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.KtVisitorVoid

@VisibleForTesting
@IntellijInternalApi
@ApiStatus.Internal
class WorkspaceInheritanceInspection : LocalInspectionTool() {
  override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): KtVisitorVoid = object : KtVisitorVoid() {
    override fun visitClassOrObject(klass: KtClassOrObject) {
      val psiForHighlighting = klass.getPsiElementForHighlighting() ?: return
      if (klass !is KtClass) return
      if (!klass.isWorkspaceEntity()) return
      if (klass.isWorkspaceEntityImplementation()) return
      if (klass.isWorkspaceEntitySource()) {
        holder.registerProblem(psiForHighlighting,
                               DevKitWorkspaceModelBundle.message("inspection.workspace.msg.entity.and.source.inheritance"),
                               ProblemHighlightType.GENERIC_ERROR_OR_WARNING)
        return
      }

      if (!klass.isInterface()) {
        holder.registerProblem(psiForHighlighting,
                               DevKitWorkspaceModelBundle.message("inspection.workspace.msg.user.implementation"),
                               ProblemHighlightType.GENERIC_ERROR_OR_WARNING)
        return
      }

      val visited = mutableSetOf<KtClass>()
      val superEntities = mutableSetOf<KtClass>()
      for (superType in klass.superTypeListEntries) {
        val resolvedSuper = superType.typeReference?.resolveToKtClass() ?: continue
        if (!visited.add(resolvedSuper)) continue
        if (!resolvedSuper.isWorkspaceEntity()) continue
        if (resolvedSuper.isAbstractEntity()) {
          superEntities.add(resolvedSuper)
          continue
        }
        holder.registerProblem(psiForHighlighting,
                               DevKitWorkspaceModelBundle.message("inspection.workspace.msg.non.abstract.inheritance"),
                               ProblemHighlightType.GENERIC_ERROR_OR_WARNING)
        return
      }
      if (superEntities.size > 1) {
        holder.registerProblem(psiForHighlighting,
                               DevKitWorkspaceModelBundle.message("inspection.workspace.msg.multiple.inheritance"),
                               ProblemHighlightType.GENERIC_ERROR_OR_WARNING)
      }
    }
  }
}
