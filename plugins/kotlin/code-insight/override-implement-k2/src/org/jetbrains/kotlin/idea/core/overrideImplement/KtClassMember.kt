// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.core.overrideImplement

import com.intellij.codeInsight.generation.ClassMember
import com.intellij.codeInsight.generation.MemberChooserObject
import com.intellij.codeInsight.generation.MemberChooserObjectBase
import com.intellij.codeInsight.generation.OverrideImplementsAnnotationsFilter
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.IntellijInternalApi
import com.intellij.openapi.util.NlsContexts
import com.intellij.openapi.util.NlsSafe
import com.intellij.psi.PsiDocCommentOwner
import com.intellij.psi.tree.TokenSet
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.kotlin.analysis.api.KaExperimentalApi
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.annotations.KaAnnotation
import org.jetbrains.kotlin.analysis.api.renderer.base.annotations.KaRendererAnnotationsFilter
import org.jetbrains.kotlin.analysis.api.renderer.declarations.KaDeclarationRenderer
import org.jetbrains.kotlin.analysis.api.renderer.declarations.impl.KaDeclarationRendererForSource
import org.jetbrains.kotlin.analysis.api.renderer.declarations.modifiers.renderers.KaRendererKeywordFilter
import org.jetbrains.kotlin.analysis.api.renderer.declarations.modifiers.renderers.KaRendererOtherModifiersProvider
import org.jetbrains.kotlin.analysis.api.renderer.declarations.renderers.callables.KaPropertyAccessorsRenderer
import org.jetbrains.kotlin.analysis.api.renderer.declarations.renderers.callables.KaValueParameterSymbolRenderer
import org.jetbrains.kotlin.analysis.api.symbols.*
import org.jetbrains.kotlin.analysis.api.symbols.pointers.KaSymbolPointer
import org.jetbrains.kotlin.analysis.utils.printer.PrettyPrinter
import org.jetbrains.kotlin.idea.base.util.names.FqNames.OptInFqNames.isRequiresOptInFqName
import org.jetbrains.kotlin.idea.core.TemplateKind
import org.jetbrains.kotlin.idea.core.getFunctionBodyTextFromTemplate
import org.jetbrains.kotlin.idea.j2k.IdeaDocCommentConverter
import org.jetbrains.kotlin.idea.kdoc.KDocElementFactory
import org.jetbrains.kotlin.lexer.KtModifierKeywordToken
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.findDocComment.findDocComment
import org.jetbrains.kotlin.psi.psiUtil.forEachDescendantOfType
import org.jetbrains.kotlin.psi.psiUtil.hasExpectModifier
import org.jetbrains.kotlin.psi.psiUtil.hasValueModifier
import org.jetbrains.kotlin.psi.psiUtil.isPropertyParameter
import org.jetbrains.kotlin.renderer.render
import org.jetbrains.kotlin.types.Variance
import javax.swing.Icon

@ApiStatus.Internal
data class KtClassMemberInfo internal constructor(
    val symbolPointer: KaSymbolPointer<KaCallableSymbol>,
    @NlsSafe val memberText: String?,
    val memberIcon: Icon?,
    @NlsContexts.Label val containingSymbolText: String?,
    val containingSymbolIcon: Icon?,
    val isProperty: Boolean,
) {
    companion object {
        context(KaSession)
        fun create(
            symbol: KaCallableSymbol,
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
            isProperty = symbol is KaPropertySymbol,
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
        KaClassOrObjectSymbolChooserObject(
            memberInfo.containingSymbolText,
            memberInfo.containingSymbolIcon
        )
    }
}

data class KaClassOrObjectSymbolChooserObject(
    @NlsContexts.Label val symbolText: String?,
    val symbolIcon: Icon?
) :
    MemberChooserObjectBase(symbolText, symbolIcon)

@IntellijInternalApi
fun createKtClassMember(
    memberInfo: KtClassMemberInfo,
    bodyType: BodyType,
    preferConstructorParameter: Boolean
): KtClassMember = KtClassMember(memberInfo, bodyType, preferConstructorParameter)

context(KaSession)
@KaExperimentalApi
@ApiStatus.Internal
fun generateMember(
    project: Project,
    ktClassMember: KtClassMember?,
    symbol: KaCallableSymbol,
    targetClass: KtClassOrObject?,
    copyDoc: Boolean,
    mode: MemberGenerateMode = MemberGenerateMode.OVERRIDE
): KtCallableDeclaration = with(ktClassMember) {
    val bodyType = getBodyType(this, symbol, targetClass, mode)

    val renderer = createRenderer(targetClass, mode, symbol)

    if (this != null && preferConstructorParameter && memberInfo.isProperty) {
        return generateConstructorParameter(project, symbol, renderer)
    }

    val newMember: KtCallableDeclaration = when (symbol) {
        is KaNamedFunctionSymbol, is KaConstructorSymbol -> generateFunction(project, symbol, renderer, mode, bodyType)
        is KaPropertySymbol -> generateProperty(project, symbol, renderer, mode, bodyType)
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
        val kDoc = when (val originalOverriddenPsi = symbol.fakeOverrideOriginal.psi) {
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

private fun getBodyType(
    ktClassMember: KtClassMember?,
    symbol: KaCallableSymbol,
    targetClass: KtClassOrObject?,
    mode: MemberGenerateMode
): BodyType {
    return when {
        mode == MemberGenerateMode.EXPECT -> BodyType.NoBody
        mode == MemberGenerateMode.ACTUAL && symbol.modality == KaSymbolModality.ABSTRACT -> BodyType.NoBody
        mode == MemberGenerateMode.ACTUAL && ktClassMember == null -> BodyType.EmptyOrTemplate
        ktClassMember == null -> BodyType.FromTemplate
        targetClass?.hasExpectModifier() == true -> BodyType.NoBody
        symbol.isExtension && mode == MemberGenerateMode.OVERRIDE -> BodyType.FromTemplate
        else -> ktClassMember.bodyType
    }
}

context(KaSession)
@KaExperimentalApi
@ApiStatus.Internal
fun generateClassWithMembers(
    project: Project,
    ktClassMember: KtClassMember?,
    symbol: KaClassSymbol,
    targetClass: KtClassOrObject?,
    mode: MemberGenerateMode = MemberGenerateMode.OVERRIDE
): KtClassOrObject = with(ktClassMember) {

    val renderer = createRenderer(targetClass, mode, symbol)

    val newClass = generateClass(project, symbol, renderer, mode)

    when (mode) {
        MemberGenerateMode.ACTUAL -> {
            newClass.addModifier(KtTokens.ACTUAL_KEYWORD)
            newClass.forEachDescendantOfType<KtDeclaration> { declaration ->
                if (!(declaration is KtParameter && !declaration.isPropertyParameter()) &&
                    declaration !is KtPropertyAccessor &&
                    declaration !is KtEnumEntry) {
                    declaration.addModifier(KtTokens.ACTUAL_KEYWORD)
                }
            }

            val primaryConstructor = if ((symbol.psi as? KtClass)?.primaryConstructor != null) {
                // renderer skips constructors without parameters/annotations
                // though they are required to have explicit actual modifier
                (newClass as? KtClass)?.createPrimaryConstructorIfAbsent()
            } else {
                newClass.primaryConstructor
            }
            primaryConstructor?.addModifier(KtTokens.ACTUAL_KEYWORD)
        }
        MemberGenerateMode.EXPECT -> if (targetClass == null) {
            newClass.addModifier(KtTokens.EXPECT_KEYWORD)
        }

        MemberGenerateMode.OVERRIDE -> {
            //  an `expect` member.
        }
    }
    return newClass
}

context(KaSession)
@KaExperimentalApi
private fun createRenderer(
    targetClass: KtClassOrObject?,
    mode: MemberGenerateMode,
    symbol: KaDeclarationSymbol
): KaDeclarationRenderer {
    val containingKtFile = targetClass?.containingKtFile

    val renderer = KaDeclarationRendererForSource.WITH_QUALIFIED_NAMES.with {
        if (mode == MemberGenerateMode.OVERRIDE) {
            annotationRenderer = annotationRenderer.with {
                annotationFilter = KaRendererAnnotationsFilter { annotation, _ -> keepAnnotation(annotation, containingKtFile) }
            }
        }

        modifiersRenderer = modifiersRenderer.with {
            keywordsRenderer = keywordsRenderer.with {
                keywordFilter = KaRendererKeywordFilter.without(
                    TokenSet.orSet(
                        KtTokens.VISIBILITY_MODIFIERS,
                        TokenSet.create(KtTokens.OPERATOR_KEYWORD, KtTokens.INFIX_KEYWORD, KtTokens.LATEINIT_KEYWORD),
                    )
                )
            }

            if (mode != MemberGenerateMode.ACTUAL) {
                modalityProvider = modalityProvider.onlyIf { s -> s != symbol }
            }

            valueParameterRenderer = object: KaValueParameterSymbolRenderer {
                // to delete when KT-75386 is fixed
                override fun renderSymbol(
                    analysisSession: KaSession,
                    symbol: KaValueParameterSymbol,
                    declarationRenderer: KaDeclarationRenderer,
                    printer: PrettyPrinter,
                ) {
                    printer {
                        if (symbol.generatedPrimaryConstructorProperty != null) {
                            val mutabilityKeyword = if (symbol.isVal) KtTokens.VAL_KEYWORD else KtTokens.VAR_KEYWORD
                            printer.append(mutabilityKeyword.value).append(" ")
                        }
                        " = ".separated(
                            {
                                declarationRenderer.callableSignatureRenderer
                                    .renderCallableSignature(analysisSession, symbol, keyword = null, declarationRenderer, printer)
                            },
                            { declarationRenderer.parameterDefaultValueRenderer.renderDefaultValue(analysisSession, symbol, printer) },
                        )
                    }
                }
            }
            propertyAccessorsRenderer = KaPropertyAccessorsRenderer.NONE

            val containingSymbol = targetClass?.symbol as? KaClassSymbol
            otherModifiersProvider = object : KaRendererOtherModifiersProvider {
                //copy from KaRendererOtherModifiersProvider.ALL with `actual` and `override` specifics
                override fun getOtherModifiers(
                    analysisSession: KaSession,
                    s: KaDeclarationSymbol
                ): List<KtModifierKeywordToken> = buildList {
                    if (mode == MemberGenerateMode.OVERRIDE && containingSymbol?.isActual == true) {
                        //include actual modifier explicitly when containing class has modifier
                        fun shouldHaveActualModifier(): Boolean {
                            if (s.isActual) return true
                            val containingInterface = s.containingSymbol
                            return containingInterface is KaClassSymbol &&
                                    containingSymbol.getExpectsForActual().any { (it as? KaClassSymbol)?.isSubClassOf(containingInterface) == true }
                        }
                        if (shouldHaveActualModifier()) {
                            add(KtTokens.ACTUAL_KEYWORD)
                        }
                    }

                    if (s is KaNamedFunctionSymbol) {
                        if (s.isExternal) add(KtTokens.EXTERNAL_KEYWORD)
                        if (s.isOverride) add(KtTokens.OVERRIDE_KEYWORD)
                        if (s.isInline) add(KtTokens.INLINE_KEYWORD)
                        if (s.isInfix) add(KtTokens.INFIX_KEYWORD)
                        if (s.isOperator) add(KtTokens.OPERATOR_KEYWORD)
                        if (s.isSuspend) add(KtTokens.SUSPEND_KEYWORD)
                    }

                    if (s is KaPropertySymbol) {
                        if (s.isOverride) add(KtTokens.OVERRIDE_KEYWORD)
                    }

                    if (s is KaValueParameterSymbol) {
                        if (s.isVararg) add(KtTokens.VARARG_KEYWORD)
                        if (s.isCrossinline) add(KtTokens.CROSSINLINE_KEYWORD)
                        if (s.isNoinline) add(KtTokens.NOINLINE_KEYWORD)
                    }

                    if (s is KaKotlinPropertySymbol) {
                        if (s.isConst) add(KtTokens.CONST_KEYWORD)
                        if (s.isLateInit) add(KtTokens.LATEINIT_KEYWORD)
                    }

                    if (s is KaNamedClassSymbol) {
                        if (s.isExternal) add(KtTokens.EXTERNAL_KEYWORD)
                        if (s.isInline) {
                            if ((s.psi as? KtClassOrObject)?.modifierList?.hasValueModifier() == true) {
                                add(KtTokens.VALUE_KEYWORD)
                            } else {
                                add(KtTokens.INLINE_KEYWORD)
                            }
                        }
                        if (s.isData) add(KtTokens.DATA_KEYWORD)
                        if (s.isFun) add(KtTokens.FUN_KEYWORD)
                        if (s.isInner) add(KtTokens.INNER_KEYWORD)
                    }

                    if (s is KaTypeParameterSymbol) {
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
    return renderer
}

/**
 * Returns true if the annotation itself is marked with @RequiresOptIn (or the old @Experimental), or if an extension wants to keep it.
 */
context(KaSession)
private fun keepAnnotation(annotation: KaAnnotation, file: KtFile?): Boolean {
    val classId = annotation.classId ?: return false
    val symbol = findClass(classId)

    if (symbol != null && symbol.hasRequiresOptInAnnotation()) return true

    return file?.let { OverrideImplementsAnnotationsFilter.keepAnnotationOnOverrideMember(classId.asFqNameString(), it) } == true
}

context(KaSession)
private fun KaClassSymbol.hasRequiresOptInAnnotation(): Boolean = annotations.any { annotation ->
    isRequiresOptInFqName(annotation.classId?.asSingleFqName())
}

context(KaSession)
@KaExperimentalApi
private fun generateConstructorParameter(
    project: Project,
    symbol: KaCallableSymbol,
    renderer: KaDeclarationRenderer,
): KtCallableDeclaration {
    return KtPsiFactory(project).createParameter(symbol.render(renderer))
}

context(KaSession)
@KaExperimentalApi
private fun generateFunction(
    project: Project,
    symbol: KaFunctionSymbol,
    renderer: KaDeclarationRenderer,
    mode: MemberGenerateMode,
    bodyType: BodyType,
): KtCallableDeclaration {
    val returnType = symbol.returnType
    val returnsUnit = returnType.isUnitType

    val body = if (bodyType != BodyType.NoBody && (symbol is KaNamedFunctionSymbol || symbol is KaConstructorSymbol && !symbol.isPrimary) && (mode != MemberGenerateMode.ACTUAL || symbol.modality != KaSymbolModality.ABSTRACT)) {
        val delegation = generateUnsupportedOrSuperCall(project, symbol, bodyType, returnsUnit)
        val returnPrefix = if (!returnsUnit && bodyType.requiresReturn) "return " else ""
        "{$returnPrefix$delegation\n}"
    } else ""

    val factory = KtPsiFactory(project)
    val functionText = symbol.render(renderer) + body

    if (symbol is KaConstructorSymbol) {
        return if (symbol.isPrimary) {
            factory.createPrimaryConstructor(functionText)
        } else {
            factory.createSecondaryConstructor(functionText)
        }
    }
    return factory.createFunction(functionText)
}

context(KaSession)
@KaExperimentalApi
private fun generateClass(
    project: Project,
    symbol: KaClassSymbol,
    renderer: KaDeclarationRenderer,
    mode: MemberGenerateMode,
): KtClassOrObject {
    val factory = KtPsiFactory(project)
    val classText = symbol.render(renderer)
    val klass = if (symbol.classKind.isObject) factory.createObject(classText) else factory.createClass(classText)
    symbol.combinedDeclaredMemberScope.declarations.mapNotNull { declaration ->
        if (declaration.origin == KaSymbolOrigin.SOURCE_MEMBER_GENERATED) {
            // ignore generated Enum#values()
            return@mapNotNull null
        }
        when (declaration) {
            is KaNamedFunctionSymbol, is KaConstructorSymbol -> {
                generateFunction(project, declaration, renderer, mode, getBodyType(null, declaration, null, mode))
            }
            is KaPropertySymbol -> {
                if (declaration.psi !is KtParameter) {
                    generateProperty(project, declaration, renderer, mode, getBodyType(null, declaration, null, mode))
                } else {
                    null
                }
            }
            is KaClassSymbol -> {
                generateClass(project, declaration, renderer, mode)
            }
            is KaEnumEntrySymbol -> {
                KtPsiFactory(project).createEnumEntry(declaration.render(renderer))
            }
            else -> {
                error("Unsupported declaration type: ${declaration::class}")
            }
        }
    }.forEach { declaration ->
        if (declaration !is KtPrimaryConstructor) {
            if (klass.declarations.any { it is KtEnumEntry }) {
                val body = klass.body
                val rBrace = body?.rBrace
                if (rBrace != null) {
                    if (declaration is KtEnumEntry) {
                        body.addBefore(factory.createComma(), rBrace)
                    } else {
                        body.addBefore(factory.createSemicolon(), rBrace)
                    }
                }
            }
            klass.addDeclaration(declaration)
        }
    }

    return klass
}

context(KaSession)
@KaExperimentalApi
private fun generateProperty(
    project: Project,
    symbol: KaPropertySymbol,
    renderer: KaDeclarationRenderer,
    mode: MemberGenerateMode,
    bodyType: BodyType,
): KtCallableDeclaration {
    val returnType = symbol.returnType
    val returnsNotUnit = !returnType.isUnitType

    val body = if (bodyType != BodyType.NoBody && (mode != MemberGenerateMode.ACTUAL || symbol.modality != KaSymbolModality.ABSTRACT)) {
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

@OptIn(KaExperimentalApi::class)
fun <T> KaSession.generateUnsupportedOrSuperCall(
    project: Project, symbol: T, bodyType: BodyType, canBeEmpty: Boolean = true
): String where T : KaCallableSymbol {
    when (bodyType.effectiveBodyType(canBeEmpty)) {
        BodyType.EmptyOrTemplate -> return ""
        BodyType.FromTemplate -> {
            val templateKind = when (symbol) {
                is KaNamedFunctionSymbol, is KaConstructorSymbol -> TemplateKind.FUNCTION
                is KaPropertySymbol -> TemplateKind.PROPERTY_INITIALIZER
                else -> throw IllegalArgumentException("$symbol must be either a function or a property")
            }
            return getFunctionBodyTextFromTemplate(
                project, templateKind, symbol.name?.asString(), symbol.returnType.render(position = Variance.OUT_VARIANCE), null
            )
        }

        else -> return buildString {
            if (bodyType is BodyType.Delegate) {
                append(bodyType.receiverName)
            } else {
                append("super")
                if (bodyType == BodyType.QualifiedSuper) {
                    val superClassFqName = (symbol.fakeOverrideOriginal.containingSymbol as? KaClassSymbol)?.name?.render()
                    superClassFqName?.let {
                        append("<").append(superClassFqName).append(">")
                    }
                }
            }
            val name = symbol.name
            if (name != null) {
                append(".").append(name.render())
            }

            if (symbol is KaFunctionSymbol) {
                val paramTexts = symbol.valueParameters.map {
                    val renderedName = it.name.render()
                    if (it.isVararg) "*$renderedName" else renderedName
                }
                paramTexts.joinTo(this, prefix = "(", postfix = ")")
            }
        }
    }
}
