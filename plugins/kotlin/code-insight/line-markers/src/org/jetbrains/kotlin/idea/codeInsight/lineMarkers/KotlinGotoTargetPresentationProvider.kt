// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.codeInsight.lineMarkers

import com.intellij.codeInsight.navigation.GotoTargetPresentationProvider
import com.intellij.ide.util.PsiElementListCellRenderer
import com.intellij.platform.backend.presentation.TargetPresentation
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.kotlin.idea.KotlinIcons
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.KtDeclaration
import org.jetbrains.kotlin.psi.KtNamedDeclaration
import org.jetbrains.kotlin.psi.KtObjectDeclaration
import org.jetbrains.kotlin.psi.psiUtil.getParentOfType

class KotlinGotoTargetPresentationProvider : GotoTargetPresentationProvider {

    override fun getTargetPresentation(element: PsiElement, differentNames: Boolean): TargetPresentation? {
        if (element !is KtDeclaration) return null
        if (element is KtClassOrObject) return null
        if (differentNames) return null
        val container = PsiTreeUtil.getParentOfType(element,
                                                    true,
                                                    KtNamedDeclaration::class.java) ?: return null

        if (container is KtObjectDeclaration && container.isObjectLiteral()) {
            val declaration = container.getParentOfType<KtNamedDeclaration>(true) ?: return null
            val targetPresentation = getTargetPresentation(declaration) ?: return null
            return TargetPresentation.builder(KotlinBundle.message("presentation.text.object.in.container", targetPresentation.presentableText))
              .icon(KotlinIcons.OBJECT)
              .locationText(targetPresentation.locationText, targetPresentation.locationIcon)
              .containerText(targetPresentation.containerText)
              .presentation()
        }
        return getTargetPresentation(container)
    }

    private fun getTargetPresentation(declaration: KtNamedDeclaration): TargetPresentation? {
        val presentation = declaration.presentation ?: return null
        val presentableText = presentation.presentableText ?: return null
        val ktFile = declaration.containingKtFile
        val moduleTextWithIcon = PsiElementListCellRenderer.getModuleTextWithIcon(declaration)
        return TargetPresentation.builder(presentableText)
          .icon(presentation.getIcon(true))
          .locationText(moduleTextWithIcon?.text, moduleTextWithIcon?.icon)
          .containerText(ktFile.packageFqName.asString())
          .presentation()
    }
}