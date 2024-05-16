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
import com.intellij.psi.tree.TokenSet
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
import org.jetbrains.kotlin.analysis.api.symbols.markers.KtPossibleMultiplatformSymbol
import org.jetbrains.kotlin.analysis.api.symbols.pointers.KtSymbolPointer
import org.jetbrains.kotlin.idea.base.util.names.FqNames.OptInFqNames.isRequiresOptInFqName
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
import org.jetbrains.kotlin.types.Variance
import javax.swing.Icon

@ApiStatus.Internal
data class KtClassMemberInfo internal constructor(
    val symbolPointer: KtSymbolPointer<KtCallableSymbol>,
    @NlsSafe val memberText: String?,
    val memberIcon: Icon?,
    @NlsContexts.Label val containingSymbolText: String?,
    val containingSymbolIcon: Icon?,
    val isProperty: Boolean,
) {
    companion object {
        context(KtAnalysisSession)
        fun create(
            symbol: KtCallableSymbol,
            memberText: @NlsSafe String? = null,
            memberIcon: Icon? = null,
            @NlsContexts.Label containingSymbolText: String? = null,
            containingSymbolIcon: Icon? = null,
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

context(KtAnalysisSession)
@ApiStatus.Internal
fun generateMember(
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
            keywordsRenderer = keywordsRenderer.with {
                keywordFilter = KtRendererKeywordFilter.without(
                    TokenSet.orSet(
                        KtTokens.VISIBILITY_MODIFIERS,
                        TokenSet.create(KtTokens.OPERATOR_KEYWORD, KtTokens.INFIX_KEYWORD, KtTokens.LATEINIT_KEYWORD),
                    )
                )
            }

            modalityProvider = modalityProvider.onlyIf { s -> s != symbol }

            val containingSymbol = targetClass?.getSymbol() as? KtClassOrObjectSymbol
            otherModifiersProvider = object : KtRendererOtherModifiersProvider {
                //copy from KtRendererOtherModifiersProvider.ALL with `actual` and `override` specifics
                override fun getOtherModifiers(
                    analysisSession: KtAnalysisSession,
                    s: KtDeclarationSymbol
                ): List<KtModifierKeywordToken> = buildList {
                    if (mode == MemberGenerateMode.OVERRIDE && s is KtPossibleMultiplatformSymbol && containingSymbol?.isActual == true) {
                        //include actual modifier explicitly when containing class has modifier
                        if (s.isActual) add(KtTokens.ACTUAL_KEYWORD)
                    }

                    if (s is KtFunctionSymbol) {
                        if (s.isExternal) add(KtTokens.EXTERNAL_KEYWORD)
                        if (s.isOverride) add(KtTokens.OVERRIDE_KEYWORD)
                        if (s.isInline) add(KtTokens.INLINE_KEYWORD)
                        if (s.isInfix) add(KtTokens.INFIX_KEYWORD)
                        if (s.isOperator) add(KtTokens.OPERATOR_KEYWORD)
                        if (s.isSuspend) add(KtTokens.SUSPEND_KEYWORD)
                    }

                    if (s is KtPropertySymbol) {
                        if (s.isOverride) add(KtTokens.OVERRIDE_KEYWORD)
                    }

                    if (s is KtValueParameterSymbol) {
                        if (s.isVararg) add(KtTokens.VARARG_KEYWORD)
                        if (s.isCrossinline) add(KtTokens.CROSSINLINE_KEYWORD)
                        if (s.isNoinline) add(KtTokens.NOINLINE_KEYWORD)
                    }

                    if (s is KtKotlinPropertySymbol) {
                        if (s.isConst) add(KtTokens.CONST_KEYWORD)
                        if (s.isLateInit) add(KtTokens.LATEINIT_KEYWORD)
                    }

                    if (s is KtNamedClassOrObjectSymbol) {
                        if (s.isExternal) add(KtTokens.EXTERNAL_KEYWORD)
                        if (s.isInline) add(KtTokens.INLINE_KEYWORD)
                        if (s.isData) add(KtTokens.DATA_KEYWORD)
                        if (s.isFun) add(KtTokens.FUN_KEYWORD)
                        if (s.isInner) add(KtTokens.INNER_KEYWORD)
                    }

                    if (s is KtTypeParameterSymbol) {
                        if (s.isReified) add(KtTokens.REIFIED_KEYWORD)
                        when (s.variance) {
                            Variance.INVARIANT -> {}
                            Variance.IN_VARIANCE -> add(KtTokens.IN_KEYWORD)
                            Variance.OUT_VARIANCE -> add(KtTokens.OUT_KEYWORD)
                        }
                    }

                    if (s == symbol && mode == MemberGenerateMode.OVERRIDE) {
                        //include additional override modifier
                        add(KtTokens.OVERRIDE_KEYWORD)
                    }
                }
            }
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
            //  an `expect` member.
        }
    }

    if (copyDoc) {
        val kDoc = when (val originalOverriddenPsi = symbol.unwrapFakeOverrides.psi) {
            is KtDeclaration -> findDocComment(originalOverriddenPsi)

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
context(KtAnalysisSession)
private fun keepAnnotation(annotation: KtAnnotationApplication, file: KtFile?): Boolean {
    val classId = annotation.classId ?: return false
    val symbol = getClassOrObjectSymbolByClassId(classId)

    if (symbol != null && symbol.hasRequiresOptInAnnotation()) return true

    return file?.let { OverrideImplementsAnnotationsFilter.keepAnnotationOnOverrideMember(classId.asFqNameString(), it) } == true
}

context(KtAnalysisSession)
private fun KtClassOrObjectSymbol.hasRequiresOptInAnnotation(): Boolean = annotations.any { annotation ->
    isRequiresOptInFqName(annotation.classId?.asSingleFqName())
}

context(KtAnalysisSession)
private fun generateConstructorParameter(
    project: Project,
    symbol: KtCallableSymbol,
    renderer: KtDeclarationRenderer,
): KtCallableDeclaration {
    return KtPsiFactory(project).createParameter(symbol.render(renderer))
}

context(KtAnalysisSession)
private fun generateFunction(
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

context(KtAnalysisSession)
private fun generateProperty(
    project: Project,
    symbol: KtPropertySymbol,
    renderer: KtDeclarationRenderer,
    bodyType: BodyType,
): KtCallableDeclaration {
    val returnType = symbol.returnType
    val returnsNotUnit = !returnType.isUnit

    val body = if (bodyType != BodyType.NoBody) {
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
    project: Project, symbol: T, bodyType: BodyType, canBeEmpty: Boolean = true
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
                project, templateKind, symbol.name.asString(), symbol.returnType.render(position = Variance.OUT_VARIANCE), null
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
