// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.kotlin.inspections

import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.psi.PsiElementVisitor
import org.jetbrains.idea.devkit.inspections.DevKitInspectionUtil
import org.jetbrains.idea.devkit.inspections.ExtensionUtil
import org.jetbrains.idea.devkit.kotlin.DevKitKotlinBundle
import org.jetbrains.kotlin.asJava.toLightClass
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtObjectDeclaration
import org.jetbrains.kotlin.psi.KtVisitorVoid
import org.jetbrains.kotlin.psi.psiUtil.getStrictParentOfType

internal class CompanionObjectInExtensionInspection : LocalInspectionTool() {

  override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor {
    if (!DevKitInspectionUtil.isAllowed(holder.file)) return PsiElementVisitor.EMPTY_VISITOR

    return object : KtVisitorVoid() {

      override fun visitObjectDeclaration(companionObject: KtObjectDeclaration) {
        if (!companionObject.isCompanion()) return

        val ktLightClass = companionObject.getStrictParentOfType<KtClass>()?.toLightClass() ?: return

        if (!ExtensionUtil.isExtensionPointImplementationCandidate(ktLightClass)) return
        if (!ExtensionUtil.isInstantiatedExtension(ktLightClass) { ExtensionUtil.hasServiceBeanFqn(it) }) return

        val anchor = companionObject.modifierList?.getModifier(KtTokens.COMPANION_KEYWORD) ?: return

        val companionObjectInExtensionInspectionSupport = CompanionObjectInExtensionInspectionSupport.getInstance()
        if (companionObject.declarations.isEmpty()) {
          holder.registerProblem(
            anchor,
            DevKitKotlinBundle.message("inspections.empty.companion.object.in.extension.message"),
            ProblemHighlightType.WARNING,
            companionObjectInExtensionInspectionSupport.createRemoveEmptyCompanionObjectFix(companionObject)
          )
        }
        else {
          val prohibitedDeclarations = companionObjectInExtensionInspectionSupport.getProhibitedDeclarations(companionObject)
          if (prohibitedDeclarations.isNotEmpty()) {
            holder.registerProblem(
              anchor,
              DevKitKotlinBundle.message("inspections.companion.object.in.extension.message"),
              ProblemHighlightType.WARNING,
              companionObjectInExtensionInspectionSupport.createMoveProhibitedDeclarationsToTopLevelFix(companionObject),
              companionObjectInExtensionInspectionSupport.createCreateObjectAndMoveProhibitedDeclarationsQuickFix(companionObject)
            )
          }
        }
      }
    }
  }
}
