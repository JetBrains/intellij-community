// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.refactoring.safeDelete

import com.intellij.psi.PsiElement
import com.intellij.refactoring.safeDelete.usageInfo.SafeDeleteReferenceSimpleDeleteUsageInfo
import org.jetbrains.kotlin.psi.KtImportDirective
import org.jetbrains.kotlin.psi.doNotAnalyze

class SafeDeleteImportDirectiveUsageInfo(
    importDirective: KtImportDirective, declaration: PsiElement
) : SafeDeleteReferenceSimpleDeleteUsageInfo(importDirective, declaration, importDirective.containingKtFile.doNotAnalyze == null)

