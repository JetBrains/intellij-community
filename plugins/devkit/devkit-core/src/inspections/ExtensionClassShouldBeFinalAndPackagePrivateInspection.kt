// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.inspections

import com.intellij.codeInspection.IntentionWrapper
import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.lang.LanguageExtension
import com.intellij.lang.jvm.JvmModifier
import com.intellij.lang.jvm.actions.createModifierActions
import com.intellij.lang.jvm.actions.modifierRequest
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.util.IntellijInternalApi
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiElementVisitor
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.idea.devkit.DevKitBundle
import org.jetbrains.idea.devkit.util.PsiUtil

class ExtensionClassShouldBeFinalAndPackagePrivateInspection : LocalInspectionTool() {

  override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor {
    return object : PsiElementVisitor() {
      override fun visitElement(element: PsiElement) {
        val language = element.language
        val extensionClassModifiersCheckerProvider = ExtensionClassModifiersCheckerProviders.forLanguage(language) ?: return
        val extensionClassModifiersChecker = extensionClassModifiersCheckerProvider.provideExtensionClassModifiersChecker(element, holder) ?: return
        extensionClassModifiersChecker.check()
      }
    }
  }

  @IntellijInternalApi
  @ApiStatus.Internal
  interface ExtensionClassModifiersCheckerProvider {
    fun provideExtensionClassModifiersChecker(element: PsiElement, holder: ProblemsHolder): ExtensionClassModifiersChecker?
  }

  internal class ExtensionClassModifiersCheckerProviderForJava : ExtensionClassModifiersCheckerProvider {
    override fun provideExtensionClassModifiersChecker(element: PsiElement, holder: ProblemsHolder): ExtensionClassModifiersChecker? {
      if (element !is PsiClass) return null
      return ExtensionClassModifiersCheckerForJava(element, holder)
    }
  }

  internal class ExtensionClassModifiersCheckerForJava(private val aClass: PsiClass, private val holder: ProblemsHolder) : ExtensionClassModifiersChecker {
    override fun check() {
      if (!PsiUtil.isExtensionPointImplementationCandidate(aClass)) {
        return
      }
      val file = aClass.containingFile ?: return
      val isFinal = aClass.hasModifier(JvmModifier.FINAL)
      val isPackageLocal = aClass.hasModifier(JvmModifier.PACKAGE_LOCAL)
      if (isFinal && isPackageLocal) return
      if (!ExtensionUtil.isInstantiatedExtension(aClass) { false }) return
      if (!isFinal) {
        val actions = createModifierActions(aClass, modifierRequest(JvmModifier.FINAL, true))
        val fixes = IntentionWrapper.wrapToQuickFixes(actions.toTypedArray(), file)
        holder.registerProblem(aClass.identifyingElement!!, DevKitBundle.message("inspection.extension.class.should.be.final.text"), *fixes)
      }
      if (!isPackageLocal) {
        val actions = createModifierActions(aClass, modifierRequest(JvmModifier.PACKAGE_LOCAL, true))
        val fixes = IntentionWrapper.wrapToQuickFixes(actions.toTypedArray(), file)
        holder.registerProblem(aClass.identifyingElement!!, DevKitBundle.message("inspection.extension.class.should.be.package.private.text"), *fixes)
      }
    }
  }

  interface ExtensionClassModifiersChecker {
    fun check()
  }
}

private val EP_NAME = ExtensionPointName.create<ExtensionClassShouldBeFinalAndPackagePrivateInspection.ExtensionClassModifiersCheckerProvider>(
  "DevKit.lang.extensionClassModifiersCheckerProvider"
)
private object ExtensionClassModifiersCheckerProviders : LanguageExtension<ExtensionClassShouldBeFinalAndPackagePrivateInspection.ExtensionClassModifiersCheckerProvider>(EP_NAME.name)