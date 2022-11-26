// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.base.codeInsight

import com.intellij.psi.PsiElement
import com.intellij.ui.IconManager
import com.intellij.ui.PlatformIcons
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.kotlin.analysis.api.KtAnalysisSession
import org.jetbrains.kotlin.analysis.api.symbols.*
import org.jetbrains.kotlin.analysis.api.symbols.markers.KtSymbolKind
import org.jetbrains.kotlin.descriptors.Modality
import org.jetbrains.kotlin.idea.KotlinIcons
import org.jetbrains.kotlin.psi.KtElement
import javax.swing.Icon

@ApiStatus.Internal
object KotlinIconProvider {
    fun KtAnalysisSession.getIconFor(symbol: KtSymbol): Icon? {
        symbol.psi?.let { referencedPsi ->
            if (referencedPsi !is KtElement) {
                return getIconForJavaDeclaration(referencedPsi)
            }
        }

        if (symbol is KtFunctionSymbol) {
            val isAbstract = symbol.modality == Modality.ABSTRACT

            return when {
                symbol.isExtension -> {
                    if (isAbstract) KotlinIcons.ABSTRACT_EXTENSION_FUNCTION else KotlinIcons.EXTENSION_FUNCTION
                }
                symbol.symbolKind == KtSymbolKind.CLASS_MEMBER -> {
                    IconManager.getInstance().getPlatformIcon(if (isAbstract) PlatformIcons.AbstractMethod else PlatformIcons.Method)
                }
                else -> KotlinIcons.FUNCTION
            }
        }

        if (symbol is KtClassOrObjectSymbol) {
            val isAbstract = (symbol as? KtNamedClassOrObjectSymbol)?.modality == Modality.ABSTRACT

            return when (symbol.classKind) {
                KtClassKind.CLASS -> if (isAbstract) KotlinIcons.ABSTRACT_CLASS else KotlinIcons.CLASS
                KtClassKind.ENUM_CLASS -> KotlinIcons.ENUM
                KtClassKind.ANNOTATION_CLASS -> KotlinIcons.ANNOTATION
                KtClassKind.OBJECT, KtClassKind.COMPANION_OBJECT -> KotlinIcons.OBJECT
                KtClassKind.INTERFACE -> KotlinIcons.INTERFACE
                KtClassKind.ANONYMOUS_OBJECT -> KotlinIcons.OBJECT
            }
        }

        return when (symbol) {
            is KtValueParameterSymbol -> KotlinIcons.PARAMETER
            is KtLocalVariableSymbol -> if (symbol.isVal) KotlinIcons.VAL else KotlinIcons.VAR
            is KtPropertySymbol -> if (symbol.isVal) KotlinIcons.FIELD_VAL else KotlinIcons.FIELD_VAR
            is KtTypeParameterSymbol -> IconManager.getInstance().getPlatformIcon(PlatformIcons.Class)
            is KtTypeAliasSymbol -> KotlinIcons.TYPE_ALIAS
            is KtEnumEntrySymbol -> KotlinIcons.ENUM
            else -> null
        }

    }

    private fun getIconForJavaDeclaration(declaration: PsiElement): Icon? {
        val defaultIconFlags = 0
        return declaration.getIcon(defaultIconFlags)
    }
}