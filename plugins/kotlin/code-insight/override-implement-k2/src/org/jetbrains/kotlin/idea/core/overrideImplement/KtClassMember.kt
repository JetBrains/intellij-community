// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.core.overrideImplement

import com.intellij.codeInsight.generation.ClassMember
import com.intellij.codeInsight.generation.MemberChooserObject
import com.intellij.codeInsight.generation.MemberChooserObjectBase
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsSafe
import com.intellij.psi.PsiDocCommentOwner
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.kotlin.analysis.api.KtAnalysisSession
import org.jetbrains.kotlin.analysis.api.renderer.declarations.KtDeclarationRenderer
import org.jetbrains.kotlin.analysis.api.renderer.declarations.impl.KtDeclarationRendererForSource
import org.jetbrains.kotlin.analysis.api.renderer.declarations.modifiers.renderers.KtRendererModifierFilter
import org.jetbrains.kotlin.analysis.api.renderer.declarations.modifiers.renderers.KtRendererOtherModifiersProvider
import org.jetbrains.kotlin.analysis.api.symbols.KtCallableSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KtDeclarationSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KtFunctionSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KtPropertySymbol
import org.jetbrains.kotlin.analysis.api.symbols.markers.KtNamedSymbol
import org.jetbrains.kotlin.idea.core.TemplateKind
import org.jetbrains.kotlin.idea.core.getFunctionBodyTextFromTemplate
import org.jetbrains.kotlin.idea.j2k.IdeaDocCommentConverter
import org.jetbrains.kotlin.idea.kdoc.KDocElementFactory
import org.jetbrains.kotlin.lexer.KtModifierKeywordToken
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.KtCallableDeclaration
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.KtDeclaration
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.psi.findDocComment.findDocComment
import org.jetbrains.kotlin.psi.psiUtil.hasExpectModifier
import org.jetbrains.kotlin.renderer.render
import org.jetbrains.kotlin.types.Variance
import javax.swing.Icon

@ApiStatus.Internal
data class KtClassMemberInfo(
    // TODO: use a `KtSymbolPointer` instead to avoid storing `KtSymbol` in an object after KT-46249 is fixed.
    val symbol: KtCallableSymbol,
    @NlsSafe val memberText: String,
    val memberIcon: Icon?,
    val containingSymbolText: String?,
    val containingSymbolIcon: Icon?
) {
    val isProperty: Boolean get() = symbol is KtPropertySymbol
}

@ApiStatus.Internal
data class KtClassMember(
    val memberInfo: KtClassMemberInfo,
    val bodyType: BodyType,
    val preferConstructorParameter: Boolean
) : MemberChooserObjectBase(
    memberInfo.memberText,
    memberInfo.memberIcon,
), ClassMember {
    val symbol = memberInfo.symbol
    override fun getParentNodeDelegate(): MemberChooserObject? =
        memberInfo.containingSymbolText?.let {
            KtClassOrObjectSymbolChooserObject(
                memberInfo.containingSymbolText,
                memberInfo.containingSymbolIcon
            )
        }
}

private data class KtClassOrObjectSymbolChooserObject(
    val symbolText: String?,
    val symbolIcon: Icon?
) :
    MemberChooserObjectBase(symbolText, symbolIcon)

internal fun createKtClassMember(
    memberInfo: KtClassMemberInfo,
    bodyType: BodyType,
    preferConstructorParameter: Boolean
): KtClassMember {
    return KtClassMember(memberInfo, bodyType, preferConstructorParameter)
}

@ApiStatus.Internal
fun KtAnalysisSession.generateMember(
    project: Project,
    ktClassMember: KtClassMember,
    targetClass: KtClassOrObject?,
    copyDoc: Boolean,
    mode: MemberGenerateMode = MemberGenerateMode.OVERRIDE
): KtCallableDeclaration = with(ktClassMember) {
    val bodyType = when {
        targetClass?.hasExpectModifier() == true -> BodyType.NoBody
        symbol.isExtension && mode == MemberGenerateMode.OVERRIDE -> BodyType.FromTemplate
        else -> bodyType
    }

    val renderer = KtDeclarationRendererForSource.WITH_QUALIFIED_NAMES.with {
        modifiersRenderer = modifiersRenderer.with {
            modifierFilter = KtRendererModifierFilter.without(KtTokens.OPERATOR_KEYWORD)

            modalityProvider = modalityProvider.onlyIf { s -> s != symbol }

            otherModifiersProvider = otherModifiersProvider and object : KtRendererOtherModifiersProvider {
                context(KtAnalysisSession)
                override fun getOtherModifiers(symbol: KtDeclarationSymbol): List<KtModifierKeywordToken> = listOf(KtTokens.OVERRIDE_KEYWORD)
            }.onlyIf { s -> mode == MemberGenerateMode.OVERRIDE && s == symbol }
        }

    }

    if (preferConstructorParameter && symbol is KtPropertySymbol) {
        return generateConstructorParameter(project, symbol, renderer)
    }


    val newMember: KtCallableDeclaration = when (symbol) {
        is KtFunctionSymbol -> generateFunction(project, symbol, renderer, bodyType)
        is KtPropertySymbol -> generateProperty(project, symbol, renderer, bodyType)
        else -> error("Unknown member to override: $symbol")
    }

    when (mode) {
        MemberGenerateMode.ACTUAL -> newMember.addModifier(KtTokens.ACTUAL_KEYWORD)
        MemberGenerateMode.EXPECT -> if (targetClass == null) {
            newMember.addModifier(KtTokens.EXPECT_KEYWORD)
        }

        MemberGenerateMode.OVERRIDE -> {
            // TODO: add `actual` keyword to the generated member if the target class has `actual` and the generated member corresponds to
            //  an `expect` member.
        }
    }

    if (copyDoc) {
        val kDoc = when (val originalOverriddenPsi = symbol.unwrapFakeOverrides.psi) {
            is KtDeclaration ->
                findDocComment(originalOverriddenPsi)

            is PsiDocCommentOwner -> {
                val kDocText = originalOverriddenPsi.docComment?.let { IdeaDocCommentConverter.convertDocComment(it) }
                if (kDocText.isNullOrEmpty()) null else KDocElementFactory(project).createKDocFromText(kDocText)
            }

            else -> null
        }
        if (kDoc != null) {
            newMember.addAfter(kDoc, null)
        }
    }

    return newMember
}

private fun KtAnalysisSession.generateConstructorParameter(
    project: Project,
    symbol: KtCallableSymbol,
    renderer: KtDeclarationRenderer,
): KtCallableDeclaration {
    return KtPsiFactory(project).createParameter(symbol.render(renderer))
}

private fun KtAnalysisSession.generateFunction(
    project: Project,
    symbol: KtFunctionSymbol,
    renderer: KtDeclarationRenderer,
    bodyType: BodyType,
): KtCallableDeclaration {
    val returnType = symbol.returnType
    val returnsUnit = returnType.isUnit

    val body = if (bodyType != BodyType.NoBody) {
        val delegation = generateUnsupportedOrSuperCall(project, symbol, bodyType, returnsUnit)
        val returnPrefix = if (!returnsUnit && bodyType.requiresReturn) "return " else ""
        "{$returnPrefix$delegation\n}"
    } else ""

    val factory = KtPsiFactory(project)
    val functionText = symbol.render(renderer) + body

    return factory.createFunction(functionText)
}

private fun KtAnalysisSession.generateProperty(
    project: Project,
    symbol: KtPropertySymbol,
    renderer: KtDeclarationRenderer,
    bodyType: BodyType,
): KtCallableDeclaration {
    val returnType = symbol.returnType
    val returnsNotUnit = !returnType.isUnit

    val body =
        if (bodyType != BodyType.NoBody) {
            buildString {
                append("\nget()")
                append(" = ")
                append(generateUnsupportedOrSuperCall(project, symbol, bodyType, !returnsNotUnit))
                if (!symbol.isVal) {
                    append("\nset(value) {}")
                }
            }
        } else ""
    return KtPsiFactory(project).createProperty(symbol.render(renderer) + body)
}

private fun <T> KtAnalysisSession.generateUnsupportedOrSuperCall(
    project: Project,
    symbol: T,
    bodyType: BodyType,
    canBeEmpty: Boolean = true
): String where T : KtNamedSymbol, T : KtCallableSymbol {
    when (bodyType.effectiveBodyType(canBeEmpty)) {
        BodyType.EmptyOrTemplate -> return ""
        BodyType.FromTemplate -> {
            val templateKind = when (symbol) {
                is KtFunctionSymbol -> TemplateKind.FUNCTION
                is KtPropertySymbol -> TemplateKind.PROPERTY_INITIALIZER
                else -> throw IllegalArgumentException("$symbol must be either a function or a property")
            }
            return getFunctionBodyTextFromTemplate(
                project,
                templateKind,
                symbol.name.asString(),
                symbol.returnType.render(position = Variance.OUT_VARIANCE),
                null
            )
        }

        else -> return buildString {
            if (bodyType is BodyType.Delegate) {
                append(bodyType.receiverName)
            } else {
                append("super")
                if (bodyType == BodyType.QualifiedSuper) {
                    val superClassFqName = symbol.originalContainingClassForOverride?.name?.render()
                    superClassFqName?.let {
                        append("<").append(superClassFqName).append(">")
                    }
                }
            }
            append(".").append(symbol.name.render())

            if (symbol is KtFunctionSymbol) {
                val paramTexts = symbol.valueParameters.map {
                    val renderedName = it.name.render()
                    if (it.isVararg) "*$renderedName" else renderedName
                }
                paramTexts.joinTo(this, prefix = "(", postfix = ")")
            }
        }
    }
}

private object RenderOptions {
    val overrideRenderOptions = KtDeclarationRendererForSource.WITH_QUALIFIED_NAMES.with {
        modifiersRenderer = modifiersRenderer.with {
            modifierFilter = KtRendererModifierFilter.onlyWith(KtTokens.OVERRIDE_KEYWORD)
        }
    }

    val actualRenderOptions = overrideRenderOptions.with {
        modifiersRenderer = modifiersRenderer.with {
            modifierFilter = modifierFilter or
                    KtRendererModifierFilter.onlyWith(KtTokens.INNER_KEYWORD) or
                    KtRendererModifierFilter.onlyWith(KtTokens.VISIBILITY_MODIFIERS)
                    KtRendererModifierFilter.onlyWith(KtTokens.MODALITY_MODIFIERS)
        }
    }
    val expectRenderOptions = actualRenderOptions.with {
        modifiersRenderer = modifiersRenderer.with {
            modifierFilter = modifierFilter or KtRendererModifierFilter.onlyWith(KtTokens.ACTUAL_KEYWORD)
        }
    }
}
