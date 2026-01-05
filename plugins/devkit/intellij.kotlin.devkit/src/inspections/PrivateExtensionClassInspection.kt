// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.kotlin.inspections

import com.intellij.codeInspection.*
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiElementVisitor
import com.intellij.psi.util.InheritanceUtil
import org.jetbrains.annotations.Nls
import org.jetbrains.idea.devkit.dom.index.IdeaPluginRegistrationIndex
import org.jetbrains.idea.devkit.inspections.ExtensionUtil
import org.jetbrains.idea.devkit.inspections.ExtensionUtil.isExtensionPointImplementationCandidate
import org.jetbrains.idea.devkit.inspections.isServiceImplementationRegisteredInPluginXml
import org.jetbrains.idea.devkit.kotlin.DevKitKotlinBundle
import org.jetbrains.kotlin.asJava.toLightClass
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtModifierListOwner
import org.jetbrains.kotlin.psi.KtVisitorVoid
import org.jetbrains.kotlin.psi.addRemoveModifier.addModifier
import org.jetbrains.kotlin.psi.psiUtil.getParentOfType
import org.jetbrains.kotlin.psi.psiUtil.isPrivate

internal class PrivateExtensionClassInspection : LocalInspectionTool() {

  override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean, session: LocalInspectionToolSession): PsiElementVisitor {
    return object : KtVisitorVoid() {
      override fun visitClass(klass: KtClass) {
        if (!klass.isPrivate()) return
        val ktLightClass = klass.toLightClass() ?: return

        if (!isExtensionPointImplementationCandidate(ktLightClass)) return

        if (isExtension(ktLightClass)
            || isRegisteredAction(ktLightClass)
            || isServiceImplementationRegisteredInPluginXml(ktLightClass)) {
          holder.registerProblem(klass.modifierList ?: klass, DevKitKotlinBundle.message("inspection.private.extension.class.text"),
                                 InternalVisibilityFix())
        }

        super.visitClass(klass)
      }
    }
  }

  private fun isExtension(psiClass: PsiClass): Boolean {
    return ExtensionUtil.isInstantiatedExtension(psiClass) { ExtensionUtil.hasServiceBeanFqn(it) }
  }

  private fun isRegisteredAction(psiClass: PsiClass): Boolean {
    return InheritanceUtil.isInheritor(psiClass, "com.intellij.openapi.actionSystem.AnAction")
           && IdeaPluginRegistrationIndex.isRegisteredActionOrGroup(psiClass, psiClass.resolveScope)
  }

  private class InternalVisibilityFix : LocalQuickFix {
    override fun getName(): @Nls String = DevKitKotlinBundle.message("inspection.private.extension.class.fix.text")
    override fun getFamilyName(): @Nls String = name

    override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
      val modifierListOwner = descriptor.psiElement.getParentOfType<KtModifierListOwner>(false)
                              ?: throw IllegalStateException("Can't find modifier list owner for modifier")
      addModifier(modifierListOwner, KtTokens.INTERNAL_KEYWORD)
    }
  }
}