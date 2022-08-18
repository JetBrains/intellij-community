// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlin.idea.fir.codeInsight.handlers.superDeclarations

import com.intellij.codeInsight.navigation.NavigationUtil
import com.intellij.ide.util.EditSourceUtil
import com.intellij.openapi.editor.Editor
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.util.PsiUtilCore
import org.jetbrains.annotations.Nls
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.analysis.api.KtAnalysisSession
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.symbols.KtCallableSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KtClassOrObjectSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KtPropertySymbol
import org.jetbrains.kotlin.analysis.api.symbols.KtSymbol
import org.jetbrains.kotlin.analysis.api.KtAllowAnalysisOnEdt
import org.jetbrains.kotlin.analysis.api.lifetime.allowAnalysisOnEdt
import org.jetbrains.kotlin.analysis.api.types.KtNonErrorClassType
import org.jetbrains.kotlin.psi.*

object KotlinSuperDeclarationsInfoService {
    fun getForDeclarationAtCaret(file: KtFile, editor: Editor): KotlinSuperDeclarationsInfo? {
        val element = file.findElementAt(editor.caretModel.offset) ?: return null
        val declaration = PsiTreeUtil.getParentOfType<KtDeclaration>(
            element,
            KtNamedFunction::class.java,
            KtClass::class.java,
            KtProperty::class.java,
            KtObjectDeclaration::class.java
        ) ?: return null

        return getForDeclaration(declaration)
    }

    fun getForDeclaration(declaration: KtDeclaration): KotlinSuperDeclarationsInfo? {
        @OptIn(KtAllowAnalysisOnEdt::class)
        return allowAnalysisOnEdt {
            analyze(declaration) {
                val symbol = declaration.getSymbol()
                // TODO add navigation to expect declarations here
                createList(symbol)
            }
        }
    }

    fun navigateToSuperDeclaration(info: KotlinSuperDeclarationsInfo, editor: Editor) {
        when (info.superDeclarations.size) {
            0 -> {
            }
            1 -> {
                val navigatable = EditSourceUtil.getDescriptor(info.superDeclarations[0])
                if (navigatable != null && navigatable.canNavigate()) {
                    navigatable.navigate(true)
                }
            }
            else -> {
                val popupTitle = getPopupTitle(info.kind)
                val superDeclarationsArray = PsiUtilCore.toPsiElementArray(info.superDeclarations)
                val popup = NavigationUtil.getPsiElementPopup(superDeclarationsArray, popupTitle)
                popup.showInBestPositionFor(editor)
            }
        }
    }

    @Nls
    private fun getPopupTitle(declarationKind: KotlinSuperDeclarationsInfo.DeclarationKind): String =
        when (declarationKind) {
            KotlinSuperDeclarationsInfo.DeclarationKind.CLASS -> KotlinBundle.message("goto.super.chooser.class.title")
            KotlinSuperDeclarationsInfo.DeclarationKind.PROPERTY -> KotlinBundle.message("goto.super.chooser.property.title")
            KotlinSuperDeclarationsInfo.DeclarationKind.FUNCTION -> KotlinBundle.message("goto.super.chooser.function.title")
        }


    private fun KtAnalysisSession.createList(symbol: KtSymbol): KotlinSuperDeclarationsInfo? = when (symbol) {
        is KtCallableSymbol -> KotlinSuperDeclarationsInfo(
            symbol.getDirectlyOverriddenSymbols().mapNotNull { it.psi },
            when (symbol) {
                is KtPropertySymbol -> KotlinSuperDeclarationsInfo.DeclarationKind.PROPERTY
                else -> KotlinSuperDeclarationsInfo.DeclarationKind.FUNCTION
            }
        )
        is KtClassOrObjectSymbol -> KotlinSuperDeclarationsInfo(
            symbol.superTypes.mapNotNull { (it as? KtNonErrorClassType)?.classSymbol?.psi },
            KotlinSuperDeclarationsInfo.DeclarationKind.CLASS,
        )

        else -> null
    }
}