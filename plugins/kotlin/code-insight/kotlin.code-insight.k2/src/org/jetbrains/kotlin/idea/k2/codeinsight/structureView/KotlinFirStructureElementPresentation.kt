// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.k2.codeinsight.structureView

import com.intellij.navigation.ColoredItemPresentation
import com.intellij.navigation.LocationPresentation
import com.intellij.openapi.editor.colors.CodeInsightColors
import com.intellij.openapi.editor.colors.TextAttributesKey
import com.intellij.openapi.util.Iconable
import com.intellij.psi.NavigatablePsiElement
import com.intellij.psi.PsiNamedElement
import com.intellij.util.PsiIconUtil
import com.intellij.util.ui.StartupUiUtil
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.components.KtDeclarationRendererOptions
import org.jetbrains.kotlin.analysis.api.components.KtTypeRendererOptions
import org.jetbrains.kotlin.analysis.api.symbols.KtCallableSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KtDeclarationSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KtSymbolOrigin
import org.jetbrains.kotlin.analysis.api.symbols.markers.KtNamedSymbol
import org.jetbrains.kotlin.analysis.api.symbols.pointers.KtSymbolPointer
import org.jetbrains.kotlin.idea.codeInsight.KotlinCodeInsightBundle
import org.jetbrains.kotlin.idea.base.codeInsight.KotlinIconProvider.getIconFor
import org.jetbrains.kotlin.psi.*
import javax.swing.Icon

private const val rightArrow = '\u2192'

internal class KotlinFirStructureElementPresentation(
    private val isInherited: Boolean,
    navigatablePsiElement: NavigatablePsiElement,
    ktElement : KtElement,
    pointer: KtSymbolPointer<*>?
) : ColoredItemPresentation, LocationPresentation {
    private val attributesKey = getElementAttributesKey(isInherited, navigatablePsiElement)
    private val elementText = getElementText(navigatablePsiElement, ktElement, pointer)
    private val locationString = getElementLocationString(isInherited, ktElement, pointer)
    private val icon = getElementIcon(navigatablePsiElement, ktElement, pointer)

    override fun getTextAttributesKey() = attributesKey

    override fun getPresentableText() = elementText

    override fun getLocationString() = locationString

    override fun getIcon(unused: Boolean) = icon

    override fun getLocationPrefix(): String {
        return if (isInherited) " " else LocationPresentation.DEFAULT_LOCATION_PREFIX
    }

    override fun getLocationSuffix(): String {
        return if (isInherited) "" else LocationPresentation.DEFAULT_LOCATION_SUFFIX
    }

    private fun getElementAttributesKey(isInherited: Boolean, navigatablePsiElement: NavigatablePsiElement): TextAttributesKey? {
        if (isInherited) {
            return CodeInsightColors.NOT_USED_ELEMENT_ATTRIBUTES
        }

        if (navigatablePsiElement is KtModifierListOwner && KtPsiUtil.isDeprecated(navigatablePsiElement)) {
            return CodeInsightColors.DEPRECATED_ATTRIBUTES
        }

        return null
    }

    private fun getElementIcon(navigatablePsiElement: NavigatablePsiElement, ktElement: KtElement, pointer: KtSymbolPointer<*>?): Icon? {
        if (navigatablePsiElement !is KtElement) {
            return navigatablePsiElement.getIcon(Iconable.ICON_FLAG_VISIBILITY)
        }

        if (pointer != null) {
            analyze(ktElement) {
                pointer.restoreSymbol()?.let {
                    return getIconFor(it)
                }
            }
        }
        if (!navigatablePsiElement.isValid) {
            return null
        }

        return PsiIconUtil.getProvidersIcon(navigatablePsiElement, Iconable.ICON_FLAG_VISIBILITY)
    }

    private fun getElementText(navigatablePsiElement: NavigatablePsiElement, ktElement : KtElement, pointer: KtSymbolPointer<*>?): String? {
        if (navigatablePsiElement is KtObjectDeclaration && navigatablePsiElement.isObjectLiteral()) {
            return KotlinCodeInsightBundle.message("object.0", (navigatablePsiElement.getSuperTypeList()?.text?.let { " : $it" } ?: ""))
        }

        if (pointer != null) {
            analyze(ktElement) {
                val symbol = pointer.restoreSymbol()
                if (symbol is KtDeclarationSymbol) {
                    return symbol.render(KtDeclarationRendererOptions(modifiers = emptySet(), renderDeclarationHeader = false, renderUnitReturnType = true, typeRendererOptions = KtTypeRendererOptions.SHORT_NAMES))
                }
            }
        }

        val text = navigatablePsiElement.name
        if (!text.isNullOrEmpty()) {
            return text
        }

        if (navigatablePsiElement is KtAnonymousInitializer) {
            return KotlinCodeInsightBundle.message("class.initializer")
        }

        return null
    }

    private fun getElementLocationString(isInherited: Boolean, ktElement: KtElement, pointer: KtSymbolPointer<*>?): String? {
        if (!isInherited || pointer == null) return null

        analyze(ktElement) {
            val symbol = pointer.restoreSymbol()
            if (symbol is KtCallableSymbol && symbol.origin == KtSymbolOrigin.SUBSTITUTION_OVERRIDE) {
                val containerPsi = symbol.psi?.parent
                if (containerPsi is PsiNamedElement) {
                    containerPsi.name?.let { 
                        return withRightArrow(it)
                    }
                }
            }

            val containingSymbol = symbol?.getContainingSymbol()
            
            if (ktElement is KtDeclaration && containingSymbol == ktElement.getSymbol()) {
                return null
            }
            
            if (containingSymbol is KtNamedSymbol) {
                return withRightArrow(containingSymbol.name.asString())
            }
        }
        return null
    }

    private fun withRightArrow(str: String): String {
        return if (StartupUiUtil.getLabelFont().canDisplay(rightArrow)) rightArrow + str else "->$str"
    }
}