// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

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
import org.jetbrains.kotlin.analysis.api.KaExperimentalApi
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.renderer.base.KaKeywordsRenderer
import org.jetbrains.kotlin.analysis.api.renderer.base.annotations.KaRendererAnnotationsFilter
import org.jetbrains.kotlin.analysis.api.renderer.declarations.KaCallableReturnTypeFilter
import org.jetbrains.kotlin.analysis.api.renderer.declarations.KaDeclarationRenderer
import org.jetbrains.kotlin.analysis.api.renderer.declarations.bodies.KaParameterDefaultValueRenderer
import org.jetbrains.kotlin.analysis.api.renderer.declarations.impl.KaDeclarationRendererForSource
import org.jetbrains.kotlin.analysis.api.renderer.declarations.modifiers.renderers.KaRendererKeywordFilter
import org.jetbrains.kotlin.analysis.api.renderer.declarations.renderers.KaTypeParametersRenderer
import org.jetbrains.kotlin.analysis.api.renderer.declarations.renderers.callables.KaConstructorSymbolRenderer
import org.jetbrains.kotlin.analysis.api.renderer.declarations.renderers.callables.KaNamedFunctionSymbolRenderer
import org.jetbrains.kotlin.analysis.api.renderer.declarations.renderers.classifiers.KaNamedClassSymbolRenderer
import org.jetbrains.kotlin.analysis.api.renderer.declarations.superTypes.KaSuperTypesFilter
import org.jetbrains.kotlin.analysis.api.symbols.*
import org.jetbrains.kotlin.analysis.api.symbols.markers.KaNamedSymbol
import org.jetbrains.kotlin.analysis.api.symbols.pointers.KaSymbolPointer
import org.jetbrains.kotlin.analysis.utils.printer.PrettyPrinter
import org.jetbrains.kotlin.idea.base.codeInsight.KotlinIconProvider.getIconFor
import org.jetbrains.kotlin.idea.codeInsight.KotlinCodeInsightBundle
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.renderer.render
import javax.swing.Icon

private const val rightArrow = '\u2192'

internal class KotlinFirStructureElementPresentation(
    private val isInherited: Boolean,
    navigatablePsiElement: NavigatablePsiElement,
    ktElement : KtElement,
    pointer: KaSymbolPointer<*>?
) : ColoredItemPresentation, LocationPresentation {
    companion object {
        @KaExperimentalApi
        private val renderer = KaDeclarationRendererForSource.WITH_SHORT_NAMES.with {
            annotationRenderer = annotationRenderer.with {
                annotationFilter = KaRendererAnnotationsFilter.NONE
            }

            modifiersRenderer = modifiersRenderer.with {
                keywordsRenderer = keywordsRenderer.with { keywordFilter = KaRendererKeywordFilter.NONE }
            }

            superTypesFilter = KaSuperTypesFilter.NONE
            typeParametersRenderer = KaTypeParametersRenderer.NO_TYPE_PARAMETERS
            keywordsRenderer = KaKeywordsRenderer.AS_WORD.with {
                keywordFilter = KaRendererKeywordFilter.onlyWith(
                    KtTokens.CONSTRUCTOR_KEYWORD,
                    KtTokens.OBJECT_KEYWORD,
                    KtTokens.COMPANION_KEYWORD
                )
            }
            returnTypeFilter = KaCallableReturnTypeFilter.ALWAYS
            namedClassRenderer = KaNamedClassSymbolRenderer.AS_SOURCE_WITHOUT_PRIMARY_CONSTRUCTOR
            parameterDefaultValueRenderer = KaParameterDefaultValueRenderer.NO_DEFAULT_VALUE
            // TODO: KTIJ-34535 custom impl of KaConstructorSymbolRenderer.AS_RAW_SIGNATURE to hit 2025.2 feature freeze window
            constructorRenderer = object : KaConstructorSymbolRenderer {
                override fun renderSymbol(
                    analysisSession: KaSession,
                    symbol: KaConstructorSymbol,
                    declarationRenderer: KaDeclarationRenderer,
                    printer: PrettyPrinter
                ) {
                    with(analysisSession) {
                        printer {
                            " ".separated(
                                {
                                    declarationRenderer.keywordsRenderer
                                        .renderKeyword(analysisSession, KtTokens.CONSTRUCTOR_KEYWORD, symbol, printer)
                                },
                                {
                                    symbol.containingDeclaration?.name?.let { printer.append(it.render()) }
                                    printer.printCollection(symbol.valueParameters, prefix = "(", postfix = ")") {
                                        withSuffix(if (it.isVararg) Typography.ellipsis.toString() else "") {
                                            declarationRenderer.typeRenderer.renderType(analysisSession, it.returnType, printer)
                                        }
                                    }
                                }
                            )
                        }
                    }
                }
            }
            // TODO: KTIJ-34535 custom impl of KaNamedFunctionSymbolRenderer.AS_RAW_SIGNATURE to hit 2025.2 feature freeze window
            namedFunctionRenderer = object : KaNamedFunctionSymbolRenderer {
                override fun renderSymbol(
                    analysisSession: KaSession,
                    symbol: KaNamedFunctionSymbol,
                    declarationRenderer: KaDeclarationRenderer,
                    printer: PrettyPrinter
                ) {
                    printer {
                        val receiverSymbol = symbol.receiverParameter
                        if (receiverSymbol != null) {
                            withSuffix(".") {
                                declarationRenderer.callableReceiverRenderer
                                    .renderReceiver(analysisSession, receiverSymbol, declarationRenderer, printer)
                            }
                        }

                        declarationRenderer.nameRenderer.renderName(analysisSession, symbol, declarationRenderer, printer)

                        printer.printCollection(symbol.valueParameters, prefix = "(", postfix = ")") {
                            withSuffix(if (it.isVararg) Typography.ellipsis.toString() else "") {
                                declarationRenderer.typeRenderer.renderType(analysisSession, it.returnType, printer)
                            }
                        }

                        withPrefix(": ") {
                            declarationRenderer.returnTypeRenderer
                                .renderReturnType(analysisSession, symbol, declarationRenderer, printer)
                        }
                    }
                }
            }
        }
    }

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

    private fun getElementIcon(navigatablePsiElement: NavigatablePsiElement, ktElement: KtElement, pointer: KaSymbolPointer<*>?): Icon? {
        if (navigatablePsiElement !is KtElement) {
            return navigatablePsiElement.getIcon(Iconable.ICON_FLAG_VISIBILITY)
        }

        if (pointer != null) {
            analyze(ktElement) {
                pointer.restoreSymbol()?.let {
                    return getIconFor(it, Iconable.ICON_FLAG_VISIBILITY)
                }
            }
        }
        if (!navigatablePsiElement.isValid) {
            return null
        }

        return PsiIconUtil.getProvidersIcon(navigatablePsiElement, Iconable.ICON_FLAG_VISIBILITY)
    }

    @OptIn(KaExperimentalApi::class)
    private fun getElementText(navigatablePsiElement: NavigatablePsiElement, ktElement : KtElement, pointer: KaSymbolPointer<*>?): String? {
        if (navigatablePsiElement is KtObjectDeclaration && navigatablePsiElement.isObjectLiteral()) {
            return KotlinCodeInsightBundle.message("object.0", (navigatablePsiElement.getSuperTypeList()?.text?.let { " : $it" } ?: ""))
        }

        if (pointer != null) {
            analyze(ktElement) {
                val symbol = pointer.restoreSymbol()
                if (symbol is KaDeclarationSymbol) {
                    return symbol.render(renderer)
                }
            }
        }

        navigatablePsiElement.name.takeUnless { it.isNullOrEmpty() }?.let { return it }

        return when (navigatablePsiElement) {
            is KtScriptInitializer -> {
                val nameReferenceExpression: KtNameReferenceExpression? =
                    navigatablePsiElement.referenceExpression()

                val referencedNameAsName = nameReferenceExpression?.getReferencedNameAsName()
                referencedNameAsName?.asString() ?: KotlinCodeInsightBundle.message("class.initializer")
            }
            is KtAnonymousInitializer -> KotlinCodeInsightBundle.message("class.initializer")
            else -> null
        }
    }

    private fun KtScriptInitializer.referenceExpression(): KtNameReferenceExpression? {
        val body = body
        return when (body) {
            is KtCallExpression -> body.calleeExpression
            is KtExpression -> body.firstChild
            else -> null
        } as? KtNameReferenceExpression
    }

    private fun getElementLocationString(isInherited: Boolean, ktElement: KtElement, pointer: KaSymbolPointer<*>?): String? {
        if (!isInherited || pointer == null) return null

        analyze(ktElement) {
            val symbol = pointer.restoreSymbol()
            if (symbol is KaCallableSymbol && symbol.origin == KaSymbolOrigin.SUBSTITUTION_OVERRIDE) {
                val containerPsi = symbol.psi?.parent
                if (containerPsi is PsiNamedElement) {
                    containerPsi.name?.let { 
                        return withRightArrow(it)
                    }
                }
            }

            val containingSymbol = symbol?.containingDeclaration
            
            if (ktElement is KtDeclaration && containingSymbol == ktElement.symbol) {
                return null
            }
            
            if (containingSymbol is KaNamedSymbol) {
                return withRightArrow(containingSymbol.name.asString())
            }
        }
        return null
    }

    private fun withRightArrow(str: String): String {
        return if (StartupUiUtil.labelFont.canDisplay(rightArrow)) rightArrow + str else "->$str"
    }
}