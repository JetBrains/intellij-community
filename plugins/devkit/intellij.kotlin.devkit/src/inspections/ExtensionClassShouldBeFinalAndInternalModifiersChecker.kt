// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.kotlin.inspections

import com.intellij.codeInsight.intention.FileModifier.SafeFieldForPreview
import com.intellij.codeInspection.LocalQuickFix
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import org.jetbrains.idea.devkit.inspections.ExtensionClassShouldBeFinalAndPackagePrivateInspection
import org.jetbrains.idea.devkit.inspections.ExtensionUtil
import org.jetbrains.idea.devkit.kotlin.DevKitKotlinBundle
import org.jetbrains.kotlin.asJava.toLightClass
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.lexer.KtModifierKeywordToken
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtModifierListOwner
import org.jetbrains.kotlin.psi.psiUtil.getParentOfType

internal class ExtensionClassShouldBeFinalAndInternalModifiersChecker : ExtensionClassShouldBeFinalAndPackagePrivateInspection.ExtensionClassModifiersCheckerProvider {

  override fun provideExtensionClassModifiersChecker(element: PsiElement,
                                            holder: ProblemsHolder): ExtensionClassShouldBeFinalAndPackagePrivateInspection.ExtensionClassModifiersChecker? {
    if (element !is KtClass) return null
    return ExtensionClassModifiersCheckerForKotlin(element, holder)
  }

  class ExtensionClassModifiersCheckerForKotlin(private val klass: KtClass,
                                       private val holder: ProblemsHolder) : ExtensionClassShouldBeFinalAndPackagePrivateInspection.ExtensionClassModifiersChecker {
    override fun check() {
      val nameIdentifier = klass.nameIdentifier ?: return
      val elementName = klass.name ?: return
      val openKeyword = klass.modifierList?.getModifier(KtTokens.OPEN_KEYWORD)
      val isOpen = openKeyword != null
      val isInternal = klass.hasModifier(KtTokens.INTERNAL_KEYWORD)
      if (!isOpen && isInternal) return
      val ktLightClass = klass.toLightClass() ?: return
      if (!ExtensionUtil.isInstantiatedExtension(ktLightClass) { false }) return
      if (isOpen) {
        val fix = ChangeModifierFix(elementName, KtTokens.OPEN_KEYWORD, true)
        holder.registerProblem(openKeyword!!, DevKitKotlinBundle.message("inspection.extension.class.should.be.final.text"), fix)
      }
      if (!isInternal) {
        val fix = ChangeModifierFix(elementName, KtTokens.INTERNAL_KEYWORD, false)
        holder.registerProblem(nameIdentifier, DevKitKotlinBundle.message("inspection.extension.class.should.be.internal.text"), fix)
      }
    }
  }

  class ChangeModifierFix(private val elementName: String,
                          @SafeFieldForPreview private val modifier: KtModifierKeywordToken,
                          private val removeModifier: Boolean = false) : LocalQuickFix {

    override fun getName(): String {
      if (removeModifier) {
        return KotlinBundle.message("remove.modifier.fix", elementName, modifier)
      }
      return KotlinBundle.message("make.0.1", elementName, modifier)
    }

    override fun getFamilyName(): String {
      if (removeModifier) {
        return KotlinBundle.message("remove.modifier.fix.family", modifier)
      }
      return KotlinBundle.message("make.0", modifier)
    }

    override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
      val modifierListOwner = descriptor.psiElement.getParentOfType<KtModifierListOwner>(true)
                              ?: throw IllegalStateException("Can't find modifier list owner for modifier")
      if (removeModifier) {
        modifierListOwner.removeModifier(modifier)
      }
      else {
        modifierListOwner.addModifier(modifier)
      }
    }
  }
}