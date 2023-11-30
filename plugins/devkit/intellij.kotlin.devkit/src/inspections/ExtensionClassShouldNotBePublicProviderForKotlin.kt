// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.kotlin.inspections

import com.intellij.codeInspection.LocalQuickFix
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.lang.jvm.JvmClass
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiFile
import org.jetbrains.idea.devkit.inspections.ExtensionClassShouldNotBePublicProvider
import org.jetbrains.kotlin.asJava.classes.KtLightClass
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.KtModifierListOwner
import org.jetbrains.kotlin.psi.psiUtil.getParentOfType
import org.jetbrains.kotlin.psi.psiUtil.isPublic

internal class ExtensionClassShouldNotBePublicProviderForKotlin : ExtensionClassShouldNotBePublicProvider {
  override fun isPublic(aClass: PsiClass): Boolean {
    return aClass is KtLightClass && aClass.kotlinOrigin?.isPublic == true
  }

  override fun provideQuickFix(clazz: JvmClass, file: PsiFile): Array<out LocalQuickFix> {
    return arrayOf(ChangeModifierFix(clazz.name!!))
  }

  override fun isApplicableForKotlin(): Boolean {
    return true
  }
}

private class ChangeModifierFix(private val elementName: String) : LocalQuickFix {

  override fun getName(): String {
    return KotlinBundle.message("make.0.1", elementName, KtTokens.INTERNAL_KEYWORD)
  }

  override fun getFamilyName(): String {
    return KotlinBundle.message("make.0", KtTokens.INTERNAL_KEYWORD)
  }

  override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
    val modifierListOwner = descriptor.psiElement.getParentOfType<KtModifierListOwner>(true) ?: return
    modifierListOwner.addModifier(KtTokens.INTERNAL_KEYWORD)
  }
}
