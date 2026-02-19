// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.refactoring.rename;

import com.intellij.openapi.util.NlsContexts
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiModifier
import com.intellij.psi.PsiParameter
import com.intellij.refactoring.JavaRefactoringSettings
import com.intellij.refactoring.RefactoringBundle
import com.intellij.refactoring.rename.naming.AutomaticParametersRenamer
import com.intellij.refactoring.rename.naming.AutomaticRenamer
import com.intellij.refactoring.rename.naming.AutomaticRenamerFactory
import com.intellij.usageView.UsageInfo
import org.jetbrains.plugins.groovy.GroovyLanguage

class GroovyAutomaticParametersRenamerFactory : AutomaticRenamerFactory {
  override fun isApplicable(element: PsiElement): Boolean {
    if (element !is PsiParameter) return false
    val declarationScope = element.declarationScope
    return declarationScope is PsiMethod && !declarationScope.hasModifierProperty(PsiModifier.STATIC)
  }

  override fun createRenamer(element: PsiElement?, newName: String?, usages: Collection<UsageInfo?>?): AutomaticRenamer =
    AutomaticParametersRenamer(element as PsiParameter, newName, GroovyLanguage)

  override fun isEnabled(): Boolean = JavaRefactoringSettings.getInstance().isRenameParameterInHierarchy
  override fun setEnabled(enabled: Boolean) {
    JavaRefactoringSettings.getInstance().isRenameParameterInHierarchy = enabled
  }

  override fun getOptionName(): @NlsContexts.Checkbox String = RefactoringBundle.message("rename.parameters.hierarchy")
}
