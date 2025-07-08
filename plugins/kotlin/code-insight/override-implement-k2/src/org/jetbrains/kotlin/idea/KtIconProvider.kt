// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea

import com.intellij.icons.AllIcons
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.util.Iconable
import com.intellij.psi.PsiClass
import com.intellij.ui.IconManager
import com.intellij.ui.RowIcon
import com.intellij.util.PlatformIcons
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.symbols.*
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtDeclaration
import javax.swing.Icon

object KtIconProvider {
    private val LOG = Logger.getInstance(KtIconProvider::class.java)

    context(KaSession)
    fun getIcon(ktSymbol: KaSymbol): Icon? {
        // logic copied from org.jetbrains.kotlin.idea.KotlinDescriptorIconProvider
        val declaration = ktSymbol.psi
        return if (declaration?.isValid == true) {
            val isClass = declaration is PsiClass || declaration is KtClass
            val flags = if (isClass) 0 else Iconable.ICON_FLAG_VISIBILITY
            if (declaration is KtDeclaration) {
                // kotlin declaration
                // visibility and abstraction better detect by a descriptor
                getIcon(ktSymbol, flags) ?: declaration.getIcon(flags)
            } else {
                // Use Java icons if it's not Kotlin declaration
                declaration.getIcon(flags)
            }
        } else {
            getIcon(ktSymbol, 0)
        }
    }

    context(KaSession)
    private fun getIcon(symbol: KaSymbol, flags: Int): Icon? {
        var result: Icon = getBaseIcon(symbol) ?: return null

        if (flags and Iconable.ICON_FLAG_VISIBILITY > 0) {
            val rowIcon = RowIcon(2)
            rowIcon.setIcon(result, 0)
            rowIcon.setIcon(getVisibilityIcon(symbol), 1)
            result = rowIcon
        }
        return result
    }

    context(KaSession)
    fun getBaseIcon(symbol: KaSymbol): Icon? {
        val isAbstract = (symbol as? KaDeclarationSymbol)?.modality == KaSymbolModality.ABSTRACT
        return when (symbol) {
            is KaPackageSymbol -> AllIcons.Nodes.Package
            is KaFunctionSymbol -> {
                val isMember = symbol.location == KaSymbolLocation.CLASS
                val isSuspend = (symbol as? KaNamedFunctionSymbol)?.isSuspend == true
                if (isSuspend) {
                    return if (isMember) KotlinIcons.SUSPEND_METHOD else KotlinIcons.SUSPEND_FUNCTION
                }
                val isExtension = symbol.isExtension
                return when {
                    isExtension && isAbstract -> KotlinIcons.ABSTRACT_EXTENSION_FUNCTION
                    isExtension && !isAbstract -> KotlinIcons.EXTENSION_FUNCTION
                    isMember && isAbstract -> PlatformIcons.ABSTRACT_METHOD_ICON
                    isMember && !isAbstract -> IconManager.getInstance().getPlatformIcon(com.intellij.ui.PlatformIcons.Method)
                    else -> KotlinIcons.FUNCTION
                }
            }
            is KaClassSymbol -> {
                when (symbol.classKind) {
                    KaClassKind.CLASS -> when {
                        isAbstract -> KotlinIcons.ABSTRACT_CLASS
                        else -> KotlinIcons.CLASS
                    }
                    KaClassKind.ENUM_CLASS -> KotlinIcons.ENUM
                    KaClassKind.ANNOTATION_CLASS -> KotlinIcons.ANNOTATION
                    KaClassKind.INTERFACE -> KotlinIcons.INTERFACE
                    KaClassKind.ANONYMOUS_OBJECT, KaClassKind.OBJECT, KaClassKind.COMPANION_OBJECT -> KotlinIcons.OBJECT
                }
            }
            is KaValueParameterSymbol -> KotlinIcons.PARAMETER
            is KaLocalVariableSymbol -> when {
                symbol.isVal -> KotlinIcons.VAL
                else -> KotlinIcons.VAR
            }
            is KaPropertySymbol -> when {
                symbol.isVal -> KotlinIcons.FIELD_VAL
                else -> KotlinIcons.FIELD_VAR
            }
            is KaTypeParameterSymbol -> IconManager.getInstance().getPlatformIcon(com.intellij.ui.PlatformIcons.Class)
            is KaTypeAliasSymbol -> KotlinIcons.TYPE_ALIAS

            else -> {
                LOG.warn("No icon for symbol: $symbol")
                null
            }
        }
    }

    context(KaSession)
    private fun getVisibilityIcon(symbol: KaSymbol): Icon? = when ((symbol as? KaDeclarationSymbol)?.visibility) {
        KaSymbolVisibility.PUBLIC -> PlatformIcons.PUBLIC_ICON
        KaSymbolVisibility.PROTECTED,
        KaSymbolVisibility.PACKAGE_PROTECTED,
        KaSymbolVisibility.PACKAGE_PRIVATE,
            -> IconManager.getInstance().getPlatformIcon(com.intellij.ui.PlatformIcons.Protected)

        KaSymbolVisibility.PRIVATE -> IconManager.getInstance().getPlatformIcon(com.intellij.ui.PlatformIcons.Private)
        KaSymbolVisibility.INTERNAL -> IconManager.getInstance().getPlatformIcon(com.intellij.ui.PlatformIcons.Local)
        else -> null
    }
}
