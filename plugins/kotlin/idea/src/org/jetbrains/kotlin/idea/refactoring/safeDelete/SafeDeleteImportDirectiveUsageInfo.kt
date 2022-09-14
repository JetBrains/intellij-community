// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.refactoring.safeDelete

import com.intellij.psi.PsiElement
import com.intellij.refactoring.safeDelete.usageInfo.SafeDeleteReferenceSimpleDeleteUsageInfo
import org.jetbrains.kotlin.asJava.unwrapped
import org.jetbrains.kotlin.idea.references.mainReference
import org.jetbrains.kotlin.psi.KtImportDirective
import org.jetbrains.kotlin.psi.KtSimpleNameExpression
import org.jetbrains.kotlin.psi.doNotAnalyze
import org.jetbrains.kotlin.psi.psiUtil.getQualifiedElementSelector

class SafeDeleteImportDirectiveUsageInfo(
    importDirective: KtImportDirective, declaration: PsiElement
) : SafeDeleteReferenceSimpleDeleteUsageInfo(importDirective, declaration, importDirective.isSafeToDelete(declaration))

private fun KtImportDirective.isSafeToDelete(element: PsiElement): Boolean {
    if (this.containingKtFile.doNotAnalyze != null) return false
    val nameExpression = importedReference?.getQualifiedElementSelector() as? KtSimpleNameExpression ?: return false
    return element.unwrapped == nameExpression.mainReference.resolve()
}
