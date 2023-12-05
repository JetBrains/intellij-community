// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.inspections

import com.intellij.codeInspection.IntentionWrapper
import com.intellij.lang.jvm.DefaultJvmElementVisitor
import com.intellij.lang.jvm.JvmClass
import com.intellij.lang.jvm.JvmElementVisitor
import com.intellij.lang.jvm.JvmModifier
import com.intellij.lang.jvm.actions.createModifierActions
import com.intellij.lang.jvm.actions.modifierRequest
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiClass
import com.intellij.psi.search.searches.DirectClassInheritorsSearch
import org.jetbrains.idea.devkit.DevKitBundle

internal class ExtensionClassShouldBeFinalAndNonPublicInspection : DevKitJvmInspection() {

  override fun buildVisitor(project: Project, sink: HighlightSink, isOnTheFly: Boolean): JvmElementVisitor<Boolean> {
    return object : DefaultJvmElementVisitor<Boolean> {
      override fun visitClass(clazz: JvmClass): Boolean {
        if (clazz !is PsiClass) return true
        if (!ExtensionUtil.isExtensionPointImplementationCandidate(clazz)) {
          return true
        }
        val sourceElement = clazz.sourceElement
        val language = sourceElement?.language ?: return true
        val file = clazz.containingFile ?: return true
        val isFinal = clazz.hasModifier(JvmModifier.FINAL)
        val extensionClassShouldNotBePublicProvider = getProvider(ExtensionClassShouldNotBePublicProviders, language) ?: return true
        val isPublic = extensionClassShouldNotBePublicProvider.isPublic(clazz)
        if (isFinal && !isPublic) return true
        if (!ExtensionUtil.isInstantiatedExtension(clazz) { false }) return true
        if (!isFinal && !haveInheritors(clazz)) {
          val actions = createModifierActions(clazz, modifierRequest(JvmModifier.FINAL, true))
          val errorMessageProvider = getProvider(ExtensionClassShouldBeFinalErrorMessageProviders, language) ?: return true
          val message = errorMessageProvider.provideErrorMessage()
          val fixes = IntentionWrapper.wrapToQuickFixes(actions.toTypedArray(), file)
          sink.highlight(message, *fixes)
        }
        if (isPublic) {
          val message = DevKitBundle.message("inspection.extension.class.should.not.be.public.text")
          val fixes = extensionClassShouldNotBePublicProvider.provideQuickFix(clazz, file)
          sink.highlight(message, *fixes)
        }
        return true
      }
    }
  }
}

private fun haveInheritors(aClass: PsiClass): Boolean {
  return DirectClassInheritorsSearch.search(aClass).any()
}
