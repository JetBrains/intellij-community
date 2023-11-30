// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.inspections

import com.intellij.codeInspection.IntentionWrapper
import com.intellij.codeInspection.LocalQuickFix
import com.intellij.lang.LanguageExtension
import com.intellij.lang.jvm.JvmClass
import com.intellij.lang.jvm.JvmModifier
import com.intellij.lang.jvm.actions.createModifierActions
import com.intellij.lang.jvm.actions.modifierRequest
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiFile

private val EP_NAME: ExtensionPointName<ExtensionClassShouldNotBePublicProvider> =
  ExtensionPointName.create("DevKit.lang.extensionClassShouldNotBePublicProvider")

internal object ExtensionClassShouldNotBePublicProviders : LanguageExtension<ExtensionClassShouldNotBePublicProvider>(EP_NAME.name)

interface ExtensionClassShouldNotBePublicProvider : JvmProvider {
  fun isPublic(aClass: PsiClass): Boolean
  fun provideQuickFix(clazz: JvmClass, file: PsiFile): Array<out LocalQuickFix>
}

internal class ExtensionClassShouldNotBePublicProviderForJVM : ExtensionClassShouldNotBePublicProvider {
  override fun isPublic(aClass: PsiClass): Boolean {
    return aClass.hasModifier(JvmModifier.PUBLIC)
  }

  override fun provideQuickFix(clazz: JvmClass, file: PsiFile): Array<out LocalQuickFix> {
    val actions = createModifierActions(clazz, modifierRequest(JvmModifier.PUBLIC, false))
    return IntentionWrapper.wrapToQuickFixes(actions.toTypedArray(), file)
  }

  override fun isApplicableForKotlin(): Boolean {
    return false
  }
}
