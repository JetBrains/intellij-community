// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.compilerPlugin.parcelize.quickfixes

import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.idea.codeinsight.api.classic.quickfixes.quickFixesPsiBasedFactory
import org.jetbrains.kotlin.idea.compilerPlugin.parcelize.KotlinParcelizeBundle
import org.jetbrains.kotlin.psi.KtProperty
import org.jetbrains.kotlin.psi.KtPsiFactory

class ParcelRemoveCustomCreatorProperty(property: KtProperty) : AbstractParcelizePsiOnlyQuickFix<KtProperty>(property) {
    override fun getText() = KotlinParcelizeBundle.message("parcelize.fix.remove.custom.creator.property")

    override fun invoke(ktPsiFactory: KtPsiFactory, element: KtProperty) {
        element.delete()
    }

    companion object {
        val FACTORY = quickFixesPsiBasedFactory<PsiElement> {
            // KtProperty or its name identifier
            val targetElement = it as? KtProperty ?: it.parent as? KtProperty ?: return@quickFixesPsiBasedFactory emptyList()
            listOf(ParcelRemoveCustomCreatorProperty(targetElement))
        }
    }
}