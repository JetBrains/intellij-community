// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.core.overrideImplement

import com.intellij.codeInsight.generation.ClassMember
import com.intellij.codeInsight.generation.MemberChooserObject
import com.intellij.codeInsight.generation.MemberChooserObjectBase
import com.intellij.codeInsight.generation.OverrideImplementsAnnotationsFilter
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsContexts
import com.intellij.openapi.util.NlsSafe
import com.intellij.psi.PsiDocCommentOwner
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.kotlin.analysis.api.KtAnalysisSession
import org.jetbrains.kotlin.analysis.api.annotations.KtAnnotationApplication
import org.jetbrains.kotlin.analysis.api.annotations.annotations
import org.jetbrains.kotlin.analysis.api.renderer.base.annotations.KtRendererAnnotationsFilter
import org.jetbrains.kotlin.analysis.api.renderer.declarations.KtDeclarationRenderer
import org.jetbrains.kotlin.analysis.api.renderer.declarations.impl.KtDeclarationRendererForSource
import org.jetbrains.kotlin.analysis.api.renderer.declarations.modifiers.renderers.KtRendererKeywordFilter
import org.jetbrains.kotlin.analysis.api.renderer.declarations.modifiers.renderers.KtRendererOtherModifiersProvider
import org.jetbrains.kotlin.analysis.api.symbols.*
import org.jetbrains.kotlin.analysis.api.symbols.markers.KtNamedSymbol
import org.jetbrains.kotlin.analysis.api.symbols.pointers.KtSymbolPointer
import org.jetbrains.kotlin.idea.base.util.names.FqNames
import org.jetbrains.kotlin.idea.core.TemplateKind
import org.jetbrains.kotlin.idea.core.getFunctionBodyTextFromTemplate
import org.jetbrains.kotlin.idea.j2k.IdeaDocCommentConverter
import org.jetbrains.kotlin.idea.kdoc.KDocElementFactory
import org.jetbrains.kotlin.lexer.KtModifierKeywordToken
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.findDocComment.findDocComment
import org.jetbrains.kotlin.psi.psiUtil.hasExpectModifier
import org.jetbrains.kotlin.renderer.render
import org.jetbrains.kotlin.resolve.checkers.OptInNames
import org.jetbrains.kotlin.types.Variance
import javax.swing.Icon

@ApiStatus.Internal
data class KtClassMemberInfo internal constructor(
    val symbolPointer: KtSymbolPointer<KtCallableSymbol>,
    @NlsSafe val memberText: String,
    val memberIcon: Icon?,
    @NlsContexts.Label val containingSymbolText: String?,
    val containingSymbolIcon: Icon?,
    val isProperty: Boolean,
) {
    companion object {
        context(KtAnalysisSession)
        fun create(
            symbol: KtCallableSymbol,
            memberText: @NlsSafe String,
            memberIcon: Icon?,
            @NlsContexts.Label containingSymbolText: String?,
            containingSymbolIcon: Icon?,
        ): KtClassMemberInfo = KtClassMemberInfo(
            symbolPointer = symbol.createPointer(),
            memberText = memberText,
            memberIcon = memberIcon,
            containingSymbolText = containingSymbolText,
            containingSymbolIcon = containingSymbolIcon,
            isProperty = symbol is KtPropertySymbol,
        )
    }
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
    override fun getParentNodeDelegate(): MemberChooserObject? = memberInfo.containingSymbolText?.let {
        KtClassOrObjectSymbolChooserObject(
            memberInfo.containingSymbolText,
            memberInfo.containingSymbolIcon
        )
    }
}

private data class KtClassOrObjectSymbolChooserObject(
    @NlsContexts.Label val symbolText: String?,
    val symbolIcon: Icon?
) :
    MemberChooserObjectBase(symbolText, symbolIcon)

internal fun createKtClassMember(
    memberInfo: KtClassMemberInfo,
    bodyType: BodyType,
    preferConstructorParameter: Boolean
): KtClassMember = KtClassMember(memberInfo, bodyType, preferConstructorParameter)

@ApiStatus.Internal
fun KtAnalysisSession.generateMember(
    project: Project,
    ktClassMember: KtClassMember,
    symbol: KtCallableSymbol,
    targetClass: KtClassOrObject?,
    copyDoc: Boolean,
    mode: MemberGenerateMode = MemberGenerateMode.OVERRIDE
): KtCallableDeclaration = with(ktClassMember) {
    val bodyType = when {
        targetClass?.hasExpectModifier() == true -> BodyType.NoBody
        symbol.isExtension && mode == MemberGenerateMode.OVERRIDE -> BodyType.FromTemplate
        else -> bodyType
    }

    val containingKtFile = targetClass?.containingKtFile

    val renderer = KtDeclarationRendererForSource.WITH_QUALIFIED_NAMES.with {
        if (mode == MemberGenerateMode.OVERRIDE) {
            annotationRenderer = annotationRenderer.with {
                annotationFilter = KtRendererAnnotationsFilter { annotation, _ -> keepAnnotation(annotation, containingKtFile) }
            }
        }

        modifiersRenderer = modifiersRenderer.with {
            keywordsRenderer = keywordsRenderer.with { keywordFilter = KtRendererKeywordFilter.without(KtTokens.OPERATOR_KEYWORD) }

            modalityProvider = modalityProvider.onlyIf { s -> s != symbol }

            otherModifiersProvider = otherModifiersProvider and object : KtRendererOtherModifiersProvider {
                context(KtAnalysisSession)
                override fun getOtherModifiers(symbol: KtDeclarationSymbol): List<KtModifierKeywordToken> =
                    listOf(KtTokens.OVERRIDE_KEYWORD)
            }.onlyIf { s -> mode == MemberGenerateMode.OVERRIDE && s == symbol }
        }
    }

    if (preferConstructorParameter && ktClassMember.memberInfo.isProperty) {
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

/**
 * Returns true if the annotation itself is marked with @RequiresOptIn (or the old @Experimental), or if an extension wants to keep it.
 */
private fun KtAnalysisSession.keepAnnotation(annotation: KtAnnotationApplication, file: KtFile?): Boolean {
    val classId = annotation.classId ?: return false
    val symbol = getClassOrObjectSymbolByClassId(classId)

    if (symbol != null && symbol.hasRequiresOptInAnnotation()) return true

    return file?.let { OverrideImplementsAnnotationsFilter.keepAnnotationOnOverrideMember(classId.asFqNameString(), it) } == true
}

context(KtAnalysisSession)
private fun KtClassOrObjectSymbol.hasRequiresOptInAnnotation(): Boolean = annotations.any { annotation ->
    val fqName = annotation.classId?.asSingleFqName()
    fqName == OptInNames.REQUIRES_OPT_IN_FQ_NAME || fqName == FqNames.OptInFqNames.OLD_EXPERIMENTAL_FQ_NAME
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
        annotationRenderer = annotationRenderer.with {
            annotationFilter = KtRendererAnnotationsFilter.NONE
        }
        modifiersRenderer = modifiersRenderer.with {
            keywordsRenderer = keywordsRenderer.with { keywordFilter = KtRendererKeywordFilter.onlyWith(KtTokens.OVERRIDE_KEYWORD) }
        }
    }

    val actualRenderOptions = overrideRenderOptions.with {
        modifiersRenderer = modifiersRenderer.with {
            keywordsRenderer = keywordsRenderer.with {
                keywordFilter =
                    keywordFilter or KtRendererKeywordFilter.onlyWith(KtTokens.INNER_KEYWORD) or KtRendererKeywordFilter.onlyWith(KtTokens.VISIBILITY_MODIFIERS)
                KtRendererKeywordFilter.onlyWith(KtTokens.MODALITY_MODIFIERS)
            }
        }
    }
    val expectRenderOptions = actualRenderOptions.with {
        modifiersRenderer = modifiersRenderer.with {
            keywordsRenderer = keywordsRenderer.with { keywordFilter = keywordFilter or KtRendererKeywordFilter.onlyWith(KtTokens.ACTUAL_KEYWORD) }
        }
    }
}
