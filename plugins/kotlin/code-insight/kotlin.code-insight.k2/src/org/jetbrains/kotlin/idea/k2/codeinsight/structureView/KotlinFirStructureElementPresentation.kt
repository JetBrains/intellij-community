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
import org.jetbrains.kotlin.analysis.api.rendering.KaPiece
import org.jetbrains.kotlin.analysis.api.rendering.KaRenderer
import org.jetbrains.kotlin.analysis.api.rendering.KaTextAttribute
import org.jetbrains.kotlin.analysis.api.rendering.append
import org.jetbrains.kotlin.analysis.api.rendering.output
import org.jetbrains.kotlin.analysis.api.rendering.push
import org.jetbrains.kotlin.analysis.api.rendering.pushEmpty
import org.jetbrains.kotlin.analysis.api.rendering.render
import org.jetbrains.kotlin.analysis.api.rendering.renderToString
import org.jetbrains.kotlin.analysis.api.session.analyze
import org.jetbrains.kotlin.analysis.api.symbols.KaCallableSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaClassKind
import org.jetbrains.kotlin.analysis.api.symbols.containingDeclaration
import org.jetbrains.kotlin.analysis.api.symbols.KaDeclarationSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaSymbolOrigin
import org.jetbrains.kotlin.analysis.api.symbols.markers.KaNamedSymbol
import org.jetbrains.kotlin.analysis.api.symbols.pointers.KaSymbolPointer
import org.jetbrains.kotlin.analysis.api.symbols.pointers.restoreSymbol
import org.jetbrains.kotlin.analysis.api.symbols.symbol
import org.jetbrains.kotlin.idea.base.codeInsight.KotlinIconProvider.getIconFor
import org.jetbrains.kotlin.idea.codeInsight.KotlinCodeInsightBundle
import org.jetbrains.kotlin.idea.util.renderSymbolName
import org.jetbrains.kotlin.idea.util.withShortDenotableSupertypes
import org.jetbrains.kotlin.idea.util.withShortNames
import org.jetbrains.kotlin.idea.util.withoutAnnotations
import org.jetbrains.kotlin.idea.util.withoutDeclarationKeywords
import org.jetbrains.kotlin.idea.util.withoutModifiers
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.name.SpecialNames
import org.jetbrains.kotlin.psi.KtAnonymousInitializer
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtDeclaration
import org.jetbrains.kotlin.psi.KtElement
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtModifierListOwner
import org.jetbrains.kotlin.psi.KtNameReferenceExpression
import org.jetbrains.kotlin.psi.KtObjectDeclaration
import org.jetbrains.kotlin.psi.KtPsiUtil
import org.jetbrains.kotlin.psi.KtScriptInitializer
import javax.swing.Icon

private const val rightArrow = '\u2192'

internal class KotlinFirStructureElementPresentation(
    private val isInherited: Boolean,
    navigatablePsiElement: NavigatablePsiElement,
    ktElement : KtElement,
    pointer: KaSymbolPointer<*>?
) : ColoredItemPresentation, LocationPresentation {
    companion object {
        private val renderer: KaRenderer = KaRenderer.withShortDenotableSupertypes.copy {
            withoutAnnotations()
            withoutModifiers()
            withoutDeclarationKeywords()
            pushEmpty(KaPiece.TypeParameterList)
            pushEmpty(KaPiece.PropertyInitializer)

            push(KaPiece.ValueParameterList) { function ->
                output.group(KaPiece.ValueParameter) {
                    output.append("(", KaTextAttribute.Punctuation)
                    function.valueParameters.forEachIndexed { index, parameter ->
                        if (index > 0) output.append(", ", KaTextAttribute.Punctuation)
                        if (parameter.isVararg) {
                            output.append(KtTokens.VARARG_KEYWORD.value, KaTextAttribute.Keyword)
                            output.space()
                        }
                        KaRenderer.withShortNames.render(parameter.returnType, output)
                    }
                    output.append(")", KaTextAttribute.Punctuation)
                }
            }

            push(KaPiece.ContextParameter) { render(it.returnType, KaPiece.Type) }

            pushEmpty(KaPiece.ValueParameterDefaultValue)
            pushEmpty(KaPiece.PropertyAccessors)

            push(KaPiece.NamedFunction) { function ->
                render(function, KaPiece.ContextParameterList)
                function.receiverParameter?.let { render(it, KaPiece.ReceiverParameter) }
                render(function, KaPiece.SymbolName)
                render(function, KaPiece.ValueParameterList)
                render(function, KaPiece.FunctionReturnType)
            }

            push(KaPiece.Constructor) { constructor ->
                output.append(KtTokens.CONSTRUCTOR_KEYWORD.value, KaTextAttribute.Keyword).space()
                constructor.containingDeclaration?.let { renderSymbolName(it) }
                render(constructor, KaPiece.ValueParameterList)
            }

            push(KaPiece.NamedClass) { classSymbol ->
                val isUnnamedCompanion = classSymbol.classKind == KaClassKind.COMPANION_OBJECT &&
                        classSymbol.name == SpecialNames.DEFAULT_NAME_FOR_COMPANION_OBJECT

                if (classSymbol.classKind == KaClassKind.COMPANION_OBJECT) {
                    output.append(KtTokens.COMPANION_KEYWORD.value, KaTextAttribute.Keyword).space()
                }
                if (classSymbol.classKind.isObject) {
                    output.append(KtTokens.OBJECT_KEYWORD.value, KaTextAttribute.Keyword)
                    if (!isUnnamedCompanion) output.space()
                }
                if (!isUnnamedCompanion) {
                    render(classSymbol, KaPiece.SymbolName)
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

    private fun getElementText(navigatablePsiElement: NavigatablePsiElement, ktElement : KtElement, pointer: KaSymbolPointer<*>?): String? {
        if (navigatablePsiElement is KtObjectDeclaration && navigatablePsiElement.isObjectLiteral()) {
            return KotlinCodeInsightBundle.message("object.0", (navigatablePsiElement.getSuperTypeList()?.text?.let { " : $it" } ?: ""))
        }

        if (pointer != null) {
            analyze(ktElement) {
                val symbol = pointer.restoreSymbol()
                if (symbol is KaDeclarationSymbol) {
                    return renderer.renderToString(symbol)
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
            if (symbol is KaCallableSymbol &&
                (symbol.origin == KaSymbolOrigin.SUBSTITUTION_OVERRIDE || symbol.origin == KaSymbolOrigin.INTERSECTION_OVERRIDE)) {
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
