// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.codeInsight

import com.intellij.psi.PsiElement
import com.intellij.usages.UsageToPsiElementProvider
import org.jetbrains.kotlin.idea.KotlinLanguage
import org.jetbrains.kotlin.psi.KtImportDirective
import org.jetbrains.kotlin.psi.KtNamedDeclaration
import org.jetbrains.kotlin.psi.psiUtil.parentsWithSelf

class KotlinUsageToPsiElementProvider : UsageToPsiElementProvider() {
    override fun getAppropriateParentFrom(element: PsiElement): PsiElement? {
        if (element.language == KotlinLanguage.INSTANCE) {
            return element.parentsWithSelf.firstOrNull { it is KtNamedDeclaration || it is KtImportDirective }
        }
        return null
    }
}
