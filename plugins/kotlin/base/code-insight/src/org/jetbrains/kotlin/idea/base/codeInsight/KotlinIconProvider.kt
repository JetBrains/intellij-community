// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.base.codeInsight

import com.intellij.openapi.util.Iconable
import com.intellij.psi.PsiElement
import com.intellij.ui.IconManager
import com.intellij.ui.PlatformIcons
import com.intellij.ui.RowIcon
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.components.containingDeclaration
import org.jetbrains.kotlin.analysis.api.symbols.*
import org.jetbrains.kotlin.idea.KotlinIcons
import org.jetbrains.kotlin.psi.KtElement
import javax.swing.Icon

@ApiStatus.Internal
object KotlinIconProvider {
    context(_: KaSession)
    fun getIconFor(symbol: KaSymbol, @Iconable.IconFlags flags: Int = 0): Icon? {
        symbol.psi?.let { referencedPsi ->
            if (referencedPsi !is KtElement) {
                return getIconForJavaDeclaration(referencedPsi, flags)
            }
        }

        val baseIcon = getBaseIcon(symbol)
        if ((flags and Iconable.ICON_FLAG_VISIBILITY) != 0) {
            val visibilityIcon = getVisibilityIcon(symbol)
            if (visibilityIcon != null) {
                val rowIcon = RowIcon(2)
                rowIcon.setIcon(baseIcon, 0)
                rowIcon.setIcon(visibilityIcon, 1)
                return rowIcon
            }
        }
        return baseIcon
    }

    context(_: KaSession)
    private fun getBaseIcon(symbol: KaSymbol): Icon? {
        if (symbol is KaNamedFunctionSymbol) {
            val isAbstract = symbol.modality == KaSymbolModality.ABSTRACT
            val suspend = symbol.isSuspend
            return when {
                symbol.isExtension -> {
                    if (isAbstract) KotlinIcons.ABSTRACT_EXTENSION_FUNCTION else KotlinIcons.EXTENSION_FUNCTION
                }
                symbol.location == KaSymbolLocation.CLASS -> {
                    if (suspend) {
                        KotlinIcons.SUSPEND_METHOD
                    } else {
                        val platformIcon = if (isAbstract) PlatformIcons.AbstractMethod else PlatformIcons.Method
                        IconManager.getInstance().getPlatformIcon(platformIcon)
                    }
                }
                else -> if (suspend) KotlinIcons.SUSPEND_FUNCTION else KotlinIcons.FUNCTION
            }
        }

        if (symbol is KaClassSymbol) {
            val isAbstract = (symbol as? KaNamedClassSymbol)?.modality == KaSymbolModality.ABSTRACT

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
            is KaPropertySymbol -> if (symbol.isVal) KotlinIcons.FIELD_VAL else KotlinIcons.FIELD_VAR
            is KaTypeParameterSymbol -> IconManager.getInstance().getPlatformIcon(PlatformIcons.Class)
            is KaTypeAliasSymbol -> KotlinIcons.TYPE_ALIAS
            is KaEnumEntrySymbol -> KotlinIcons.ENUM
            is KaConstructorSymbol -> symbol.containingDeclaration?.let { getIconFor(it) }
            else -> null
        }
    }

    private fun getVisibilityIcon(symbol: KaSymbol): Icon? {
        val visibility: KaSymbolVisibility =
            (symbol as? KaValueParameterSymbol)?.generatedPrimaryConstructorProperty?.visibility
                ?: (symbol as? KaDeclarationSymbol)?.visibility
                ?: return null
        val id = when (visibility) {
            KaSymbolVisibility.PUBLIC -> PlatformIcons.Public
            KaSymbolVisibility.PROTECTED -> PlatformIcons.Protected
            KaSymbolVisibility.PRIVATE -> PlatformIcons.Private
            KaSymbolVisibility.INTERNAL -> PlatformIcons.Local
            else -> return null
        }
        return IconManager.getInstance().getPlatformIcon(id)
    }

    private fun getIconForJavaDeclaration(declaration: PsiElement, @Iconable.IconFlags flags: Int = 0): Icon? {
        return declaration.getIcon(flags)
    }
}