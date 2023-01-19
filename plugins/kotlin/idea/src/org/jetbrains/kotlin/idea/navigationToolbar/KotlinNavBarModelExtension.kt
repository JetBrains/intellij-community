// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.navigationToolbar

import com.intellij.psi.PsiElement
import com.intellij.psi.PsiNamedElement
import org.jetbrains.kotlin.idea.KotlinIconProvider
import org.jetbrains.kotlin.idea.projectView.KtDeclarationTreeNode.Companion.tryGetRepresentableText
import org.jetbrains.kotlin.psi.KtDeclaration
import org.jetbrains.kotlin.psi.KtFile

class KotlinNavBarModelExtension : AbstractNavBarModelExtensionCompatBase() {
    override fun getPresentableText(item: Any?): String? =
        when (item) {
            is KtDeclaration -> tryGetRepresentableText(item, renderArguments = false)
            is PsiNamedElement -> item.name
            else -> null
        }

    override fun adjustElementImpl(psiElement: PsiElement?): PsiElement? {
        if (psiElement is KtDeclaration) {
            return psiElement
        }

        val containingFile = psiElement?.containingFile as? KtFile ?: return psiElement
        return KotlinIconProvider.getSingleClass(containingFile) ?: psiElement
    }
}
