// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea

import com.intellij.codeInsight.navigation.GotoTargetHandler
import com.intellij.codeInsight.navigation.GotoTargetRendererProvider
import com.intellij.ide.util.PsiElementListCellRenderer
import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.asJava.classes.KtLightClass
import org.jetbrains.kotlin.idea.presentation.DeclarationByModuleRenderer
import org.jetbrains.kotlin.idea.presentation.KtLightClassListCellRenderer
import org.jetbrains.kotlin.idea.util.isEffectivelyActual
import org.jetbrains.kotlin.psi.KtDeclaration

class KotlinGotoTargetRenderProvider : GotoTargetRendererProvider {
    override fun getRenderer(element: PsiElement, gotoData: GotoTargetHandler.GotoData): PsiElementListCellRenderer<*>? {
        return when (element) {
            is KtLightClass -> {
                // Need to override default Java render
                KtLightClassListCellRenderer()
            }
            is KtDeclaration -> {
                if (element.isEffectivelyActual()) {
                    DeclarationByModuleRenderer()
                } else {
                    null
                }
            }
            else -> {
                null
            }
        }
    }
}
