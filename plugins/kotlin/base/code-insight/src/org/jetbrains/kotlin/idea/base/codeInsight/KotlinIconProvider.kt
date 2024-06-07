// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.base.codeInsight

import com.intellij.psi.PsiElement
import com.intellij.ui.IconManager
import com.intellij.ui.PlatformIcons
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.symbols.*
import org.jetbrains.kotlin.analysis.api.symbols.markers.KaSymbolKind
import org.jetbrains.kotlin.descriptors.Modality
import org.jetbrains.kotlin.idea.KotlinIcons
import org.jetbrains.kotlin.psi.KtElement
import javax.swing.Icon

@ApiStatus.Internal
object KotlinIconProvider {
    context(KaSession)
    fun getIconFor(symbol: KtSymbol): Icon? {
        symbol.psi?.let { referencedPsi ->
            if (referencedPsi !is KtElement) {
                return getIconForJavaDeclaration(referencedPsi)
            }
        }

        if (symbol is KaFunctionSymbol) {
            val isAbstract = symbol.modality == Modality.ABSTRACT

            return when {
                symbol.isExtension -> {
                    if (isAbstract) KotlinIcons.ABSTRACT_EXTENSION_FUNCTION else KotlinIcons.EXTENSION_FUNCTION
                }
                symbol.symbolKind == KaSymbolKind.CLASS_MEMBER -> {
                    IconManager.getInstance().getPlatformIcon(if (isAbstract) PlatformIcons.AbstractMethod else PlatformIcons.Method)
                }
                else -> KotlinIcons.FUNCTION
            }
        }

        if (symbol is KaClassOrObjectSymbol) {
            val isAbstract = (symbol as? KaNamedClassOrObjectSymbol)?.modality == Modality.ABSTRACT

            return when (symbol.classKind) {
                KaClassKind.CLASS -> if (isAbstract) KotlinIcons.ABSTRACT_CLASS else KotlinIcons.CLASS
                KaClassKind.ENUM_CLASS -> KotlinIcons.ENUM
                KaClassKind.ANNOTATION_CLASS -> KotlinIcons.ANNOTATION
                KaClassKind.OBJECT, KaClassKind.COMPANION_OBJECT -> KotlinIcons.OBJECT
                KaClassKind.INTERFACE -> KotlinIcons.INTERFACE
                KaClassKind.ANONYMOUS_OBJECT -> KotlinIcons.OBJECT
            }
        }

        return when (symbol) {
            is KaValueParameterSymbol -> KotlinIcons.PARAMETER
            is KaLocalVariableSymbol -> if (symbol.isVal) KotlinIcons.VAL else KotlinIcons.VAR
            is KtPropertySymbol -> if (symbol.isVal) KotlinIcons.FIELD_VAL else KotlinIcons.FIELD_VAR
            is KaTypeParameterSymbol -> IconManager.getInstance().getPlatformIcon(PlatformIcons.Class)
            is KaTypeAliasSymbol -> KotlinIcons.TYPE_ALIAS
            is KaEnumEntrySymbol -> KotlinIcons.ENUM
            is KaConstructorSymbol -> symbol.containingSymbol?.let { getIconFor(it) }
            else -> null
        }

    }

    private fun getIconForJavaDeclaration(declaration: PsiElement): Icon? {
        val defaultIconFlags = 0
        return declaration.getIcon(defaultIconFlags)
    }
}