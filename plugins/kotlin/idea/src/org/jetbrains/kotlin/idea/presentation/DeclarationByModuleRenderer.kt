// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.presentation

import com.intellij.ide.util.PsiClassRenderingInfo
import com.intellij.psi.NavigatablePsiElement
import com.intellij.psi.PsiClass
import com.intellij.psi.presentation.java.SymbolPresentationUtil

class DeclarationByModuleRenderer : KtModuleSpecificListCellRenderer<NavigatablePsiElement>() {
    override fun getContainerText(element: NavigatablePsiElement?, name: String?): String? {
        if (element is PsiClass) {
            return PsiClassRenderingInfo.getContainerTextStatic(element)
        }
        return null
    }

    override fun getElementText(element: NavigatablePsiElement): String =
        SymbolPresentationUtil.getSymbolPresentableText(element)
}