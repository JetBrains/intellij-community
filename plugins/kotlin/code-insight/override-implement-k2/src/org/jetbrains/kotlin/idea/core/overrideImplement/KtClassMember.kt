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
import org.jetbrains.kotlin.analysis.api.KaImplementationDetail
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.annotations.KaAnnotation
import org.jetbrains.kotlin.analysis.api.base.KaContextReceiver
import org.jetbrains.kotlin.analysis.api.base.KaContextReceiversOwner
import org.jetbrains.kotlin.analysis.api.components.combinedDeclaredMemberScope
import org.jetbrains.kotlin.analysis.api.components.containingSymbol
import org.jetbrains.kotlin.analysis.api.components.declaredMemberScope
import org.jetbrains.kotlin.analysis.api.components.fakeOverrideOriginal
import org.jetbrains.kotlin.analysis.api.components.getExpectsForActual
import org.jetbrains.kotlin.analysis.api.components.isSubClassOf
import org.jetbrains.kotlin.analysis.api.components.isUnitType
import org.jetbrains.kotlin.analysis.api.components.render
import org.jetbrains.kotlin.analysis.api.renderer.base.annotations.KaRendererAnnotationsFilter
import org.jetbrains.kotlin.analysis.api.renderer.base.contextReceivers.KaContextReceiversRenderer
import org.jetbrains.kotlin.analysis.api.renderer.base.contextReceivers.renderers.KaContextReceiverLabelRenderer
import org.jetbrains.kotlin.analysis.api.renderer.base.contextReceivers.renderers.KaContextReceiverListRenderer
import org.jetbrains.kotlin.analysis.api.renderer.declarations.KaDeclarationRenderer
import org.jetbrains.kotlin.analysis.api.renderer.declarations.bodies.KaFunctionLikeBodyRenderer
import org.jetbrains.kotlin.analysis.api.renderer.declarations.impl.KaDeclarationRendererForSource
import org.jetbrains.kotlin.analysis.api.renderer.declarations.modifiers.renderers.KaRendererKeywordFilter
import org.jetbrains.kotlin.analysis.api.renderer.declarations.modifiers.renderers.KaRendererOtherModifiersProvider
import org.jetbrains.kotlin.analysis.api.renderer.declarations.renderers.callables.KaPropertyAccessorsRenderer
import org.jetbrains.kotlin.analysis.api.renderer.declarations.superTypes.KaSuperTypesCallArgumentsRenderer
import org.jetbrains.kotlin.analysis.api.renderer.types.KaTypeRenderer
import org.jetbrains.kotlin.analysis.api.symbols.*
import org.jetbrains.kotlin.analysis.api.symbols.markers.KaContextParameterOwnerSymbol
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
import org.jetbrains.kotlin.psi.psiUtil.hasActualModifier
import org.jetbrains.kotlin.psi.psiUtil.hasExpectModifier
import org.jetbrains.kotlin.psi.psiUtil.hasValueModifier
import org.jetbrains.kotlin.psi.psiUtil.isPropertyParameter
import org.jetbrains.kotlin.renderer.render
import org.jetbrains.kotlin.types.Variance
import org.jetbrains.kotlin.utils.addIfNotNull
import javax.swing.Icon

@ApiStatus.Internal
data class KtClassMemberInfo(
    val symbolPointer: KaSymbolPointer<KaCallableSymbol>,
    val memberText: @NlsSafe String?,
    val memberIcon: Icon?,
    val containingSymbolText: @NlsContexts.Label String?,
    val containingSymbolIcon: Icon?,
    val isProperty: Boolean,
) {
    companion object {
        context(_: KaSession)
        fun create(
            symbol: KaCallableSymbol,
            memberText: @NlsSafe String? = null,
            memberIcon: Icon? = null,
            containingSymbolText: @NlsContexts.Label String? = null,
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

context(_: KaSession)
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

context(_: KaSession)
private fun getBodyType(
    ktClassMember: KtClassMember?,
    symbol: KaCallableSymbol,
    targetClass: KtClassOrObject?,
    mode: MemberGenerateMode
): BodyType {
    return when {
        mode == MemberGenerateMode.EXPECT -> BodyType.NoBody
        isToKeepAbstract(mode, symbol) -> BodyType.NoBody
        mode == MemberGenerateMode.ACTUAL && ktClassMember == null -> BodyType.EmptyOrTemplate
        ktClassMember == null -> BodyType.FromTemplate
        targetClass?.hasExpectModifier() == true -> BodyType.NoBody
        symbol.isExtension && mode == MemberGenerateMode.OVERRIDE -> BodyType.FromTemplate
        else -> ktClassMember.bodyType
    }
}

context(_: KaSession)
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
                    declaration !is KtTypeParameter &&
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
        MemberGenerateMode.EXPECT -> {
            if (targetClass == null) {
                newClass.addModifier(KtTokens.EXPECT_KEYWORD)
            }

            val actuals = mutableSetOf<String>()
            symbol.psi?.forEachDescendantOfType<KtPrimaryConstructor> {
                if (it.hasActualModifier()) {
                    actuals.addIfNotNull(it.name)
                }
            }
            newClass.forEachDescendantOfType<KtPrimaryConstructor> { primaryConstructor ->
                if (primaryConstructor.name !in actuals) {
                    primaryConstructor.delete()
                } else {
                    val klass = primaryConstructor.parent as KtClass
                    if (!klass.areConstructorPropertiesAllowed()) {
                        primaryConstructor.valueParameters.forEach { param ->
                            param.valOrVarKeyword?.delete()
                        }
                    }
                }
            }
        }

        MemberGenerateMode.OVERRIDE -> {
            //  an `expect` member.
        }
    }
    return newClass
}

private fun KtClassOrObject.areConstructorPropertiesAllowed(): Boolean = this is KtClass && (isAnnotation() || isInline())

context(_: KaSession)
@KaExperimentalApi
private fun createRenderer(
    targetClass: KtClassOrObject?,
    mode: MemberGenerateMode,
    topDeclarationSymbol: KaDeclarationSymbol
): KaDeclarationRenderer {
    val containingKtFile = targetClass?.containingKtFile

    val renderer = KaDeclarationRendererForSource.WITH_QUALIFIED_NAMES.with {
        functionLikeBodyRenderer = object : KaFunctionLikeBodyRenderer {
            override fun renderBody(
                analysisSession: KaSession,
                symbol: KaFunctionSymbol,
                printer: PrettyPrinter
            ) {
                if (symbol is KaConstructorSymbol &&
                    !symbol.isPrimary &&
                    (symbol.containingSymbol as? KaClassSymbol)?.declaredMemberScope?.constructors?.any { it.isPrimary && (mode == MemberGenerateMode.ACTUAL || mode == MemberGenerateMode.EXPECT && it.isActual) } == true) {
                    printer.append(" : this()")
                }
            }
        }
        if (mode == MemberGenerateMode.OVERRIDE) {
            annotationRenderer = annotationRenderer.with {
                annotationFilter = KaRendererAnnotationsFilter { annotation, _ -> keepAnnotation(annotation, containingKtFile) }
            }
        }

        if (mode == MemberGenerateMode.EXPECT) {
            superTypesArgumentRenderer = KaSuperTypesCallArgumentsRenderer.NO_ARGS
        }

        modifiersRenderer = modifiersRenderer.with {
            visibilityProvider = visibilityProvider.onlyIf { s -> s != KtTokens.DEFAULT_VISIBILITY_KEYWORD }
            if (mode == MemberGenerateMode.OVERRIDE) {
                keywordsRenderer = keywordsRenderer.with {
                    keywordFilter = KaRendererKeywordFilter.without(
                        TokenSet.orSet(
                            KtTokens.VISIBILITY_MODIFIERS,
                            TokenSet.create(KtTokens.OPERATOR_KEYWORD, KtTokens.INFIX_KEYWORD, KtTokens.LATEINIT_KEYWORD),
                        )
                    )
                }
            }

            if (mode == MemberGenerateMode.OVERRIDE) {
                modalityProvider = modalityProvider.onlyIf { s -> s != topDeclarationSymbol }
            }

            propertyAccessorsRenderer = KaPropertyAccessorsRenderer.NONE

            val containingSymbol = targetClass?.symbol as? KaClassSymbol
            otherModifiersProvider = object : KaRendererOtherModifiersProvider {
                //copy from KaRendererOtherModifiersProvider.ALL with `actual` and `override` specifics
                override fun getOtherModifiers(
                    analysisSession: KaSession,
                    symbol: KaDeclarationSymbol
                ): List<KtModifierKeywordToken> = buildList {
                    if (mode == MemberGenerateMode.OVERRIDE && containingSymbol?.isActual == true) {
                        //include actual modifier explicitly when containing class has modifier
                        fun shouldHaveActualModifier(): Boolean {
                            if (symbol.isActual) return true
                            val containingInterface = symbol.containingSymbol
                            return containingInterface is KaClassSymbol &&
                                    containingSymbol.getExpectsForActual().any { (it as? KaClassSymbol)?.isSubClassOf(containingInterface) == true }
                        }
                        if (shouldHaveActualModifier()) {
                            add(KtTokens.ACTUAL_KEYWORD)
                        }
                    }

                    if (symbol is KaNamedFunctionSymbol) {
                        if (symbol.isExternal) add(KtTokens.EXTERNAL_KEYWORD)
                        if (symbol.isOverride) add(KtTokens.OVERRIDE_KEYWORD)
                        if (symbol.isInline) add(KtTokens.INLINE_KEYWORD)
                        if (symbol.isInfix) add(KtTokens.INFIX_KEYWORD)
                        if (symbol.isOperator) add(KtTokens.OPERATOR_KEYWORD)
                        if (symbol.isSuspend) add(KtTokens.SUSPEND_KEYWORD)
                    }

                    if (symbol is KaPropertySymbol) {
                        if (symbol.isOverride) add(KtTokens.OVERRIDE_KEYWORD)
                    }

                    if (symbol is KaValueParameterSymbol) {
                        if (symbol.isVararg) add(KtTokens.VARARG_KEYWORD)
                        if (symbol.isCrossinline) add(KtTokens.CROSSINLINE_KEYWORD)
                        if (symbol.isNoinline) add(KtTokens.NOINLINE_KEYWORD)
                    }

                    if (symbol is KaKotlinPropertySymbol) {
                        if (symbol.isConst) add(KtTokens.CONST_KEYWORD)
                        if (symbol.isLateInit) add(KtTokens.LATEINIT_KEYWORD)
                    }

                    if (symbol is KaNamedClassSymbol) {
                        if (symbol.isExternal) add(KtTokens.EXTERNAL_KEYWORD)
                        if (symbol.isInline) {
                            if ((symbol.psi as? KtClassOrObject)?.modifierList?.hasValueModifier() == true) {
                                add(KtTokens.VALUE_KEYWORD)
                            } else {
                                add(KtTokens.INLINE_KEYWORD)
                            }
                        }
                        if (mode != MemberGenerateMode.EXPECT && symbol.isData) add(KtTokens.DATA_KEYWORD)
                        if (symbol.isFun) add(KtTokens.FUN_KEYWORD)
                        if (symbol.isInner) add(KtTokens.INNER_KEYWORD)
                    }

                    if (symbol is KaTypeParameterSymbol) {
                        if (symbol.isReified) add(KtTokens.REIFIED_KEYWORD)
                        when (symbol.variance) {
                            Variance.INVARIANT -> {}
                            Variance.IN_VARIANCE -> add(KtTokens.IN_KEYWORD)
                            Variance.OUT_VARIANCE -> add(KtTokens.OUT_KEYWORD)
                        }
                    }

                    if (symbol == topDeclarationSymbol && mode == MemberGenerateMode.OVERRIDE) {
                        //include additional override modifier
                        add(KtTokens.OVERRIDE_KEYWORD)
                    }
                }
            }
        }

        withoutLabel()
    }
    return renderer
}

@OptIn(KaExperimentalApi::class, KaImplementationDetail::class)
fun KaDeclarationRenderer.Builder.withoutLabel() {
    contextReceiversRenderer = contextReceiversRenderer.with {
        contextReceiverListRenderer = ContextParametersListRenderer
    }

    typeRenderer = typeRenderer.with {
        contextReceiversRenderer = contextReceiversRenderer.with {
            contextReceiverListRenderer = ContextParametersListRenderer
        }
    }
}

//todo rewrite after KT-66192 is implemented
@OptIn(KaExperimentalApi::class, KaImplementationDetail::class)
object ContextParametersListRenderer: KaContextReceiverListRenderer {
    override fun renderContextReceivers(
        analysisSession: KaSession,
        owner: KaContextReceiversOwner,
        contextReceiversRenderer: KaContextReceiversRenderer,
        typeRenderer: KaTypeRenderer,
        printer: PrettyPrinter
    ) {
        if (owner is KaContextParameterOwnerSymbol && owner.contextParameters.any { it.psi is KtParameter }) {
            printer {
                append("context(")
                printCollection(owner.contextParameters) { contextParameter ->

                    append((contextParameter.psi as? KtParameter)?.name ?: contextParameter.name.render())
                    append(": ")

                    typeRenderer.renderType(analysisSession, contextParameter.returnType, printer)
                }
                append(")")
            }
        } else {
            val contextReceivers = owner.contextReceivers
            if (contextReceivers.isEmpty()) return

            printer {
                append("context(")
                printCollection(contextReceivers) { contextReceiver ->
                    typeRenderer.renderType(analysisSession, contextReceiver.type, printer)
                }
                append(")")
            }
        }
    }
}

@OptIn(KaExperimentalApi::class)
object WITHOUT_LABEL : KaContextReceiverLabelRenderer {

    override fun renderLabel(
        analysisSession: KaSession,
        contextReceiver: KaContextReceiver,
        contextReceiversRenderer: KaContextReceiversRenderer,
        printer: PrettyPrinter,
    ) {
    }
}

/**
 * Returns true if the annotation itself is marked with @RequiresOptIn (or the old @Experimental), or if an extension wants to keep it.
 */
context(_: KaSession)
private fun keepAnnotation(annotation: KaAnnotation, file: KtFile?): Boolean {
    val classId = annotation.classId ?: return false
    val symbol = findClass(classId)

    if (symbol != null && symbol.hasRequiresOptInAnnotation()) return true

    return file?.let { OverrideImplementsAnnotationsFilter.keepAnnotationOnOverrideMember(classId.asFqNameString(), it) } == true
}

context(_: KaSession)
private fun KaClassSymbol.hasRequiresOptInAnnotation(): Boolean = annotations.any { annotation ->
    isRequiresOptInFqName(annotation.classId?.asSingleFqName())
}

context(_: KaSession)
@KaExperimentalApi
private fun generateConstructorParameter(
    project: Project,
    symbol: KaCallableSymbol,
    renderer: KaDeclarationRenderer,
): KtCallableDeclaration {
    return KtPsiFactory(project).createParameter(symbol.render(renderer))
}

context(_: KaSession)
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

    val canHaveBody = symbol is KaNamedFunctionSymbol || symbol is KaConstructorSymbol && !symbol.isPrimary
    val isToKeepAbstract = isToKeepAbstract(mode, symbol)
    val body = when {
        bodyType == BodyType.NoBody -> ""
        canHaveBody && !isToKeepAbstract -> {
            val delegation = generateUnsupportedOrSuperCall(project, symbol, bodyType, returnsUnit)
            val returnPrefix = if (!returnsUnit && bodyType.requiresReturn) "return " else ""
            "{$returnPrefix$delegation\n}"
        }
        else -> ""
    }

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

context(_: KaSession)
@KaExperimentalApi
private fun generateClass(
    project: Project,
    symbol: KaClassSymbol,
    renderer: KaDeclarationRenderer,
    mode: MemberGenerateMode,
): KtClassOrObject {
    val factory = KtPsiFactory(project)
    val classText = symbol.render(renderer)
    val klass = when (symbol.classKind) {
        KaClassKind.COMPANION_OBJECT -> {
            factory.createCompanionObject(classText)
        }

        KaClassKind.OBJECT, KaClassKind.ANONYMOUS_OBJECT -> {
            factory.createObject(classText)
        }

        else -> {
            factory.createClass(classText)
        }
    }
    val constructorSymbol = symbol.declaredMemberScope.constructors.find { it.isPrimary }
    if (constructorSymbol != null) {
        val primaryConstructor = klass.primaryConstructor
        if (primaryConstructor != null) {
            constructorSymbol.valueParameters.forEachIndexed { index, symbol ->
                if (symbol.generatedPrimaryConstructorProperty != null) {
                    val mutabilityKeyword = if (symbol.isVal) factory.createValKeyword() else factory.createVarKeyword()
                    val parameter = primaryConstructor.valueParameters[index]
                    parameter.addBefore(mutabilityKeyword, parameter.nameIdentifier)
                }
            }
        }
    }
    symbol.combinedDeclaredMemberScope.declarations.mapNotNull { declaration ->
        if (declaration.origin == KaSymbolOrigin.SOURCE_MEMBER_GENERATED) {
            // ignore generated Enum#values()
            return@mapNotNull null
        }

        if (mode == MemberGenerateMode.EXPECT && !declaration.isActual && declaration !is KaEnumEntrySymbol) {
            return@mapNotNull null
        }

        when (declaration) {
            is KaNamedFunctionSymbol, is KaConstructorSymbol -> {
                generateFunction(project, declaration, renderer, mode, getBodyType(null, declaration, null, mode))
            }
            is KaPropertySymbol -> {
                if (declaration.psi !is KtParameter ||
                    declaration.isActual && mode == MemberGenerateMode.EXPECT && !klass.areConstructorPropertiesAllowed()) {
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
            val hasEnumConstants = klass.declarations.any { it is KtEnumEntry }
            if (hasEnumConstants) {
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

            if (klass is KtClass && klass.isEnum() && !hasEnumConstants && declaration !is KtEnumEntry) {
                val body = klass.getOrCreateBody()
                body.addBefore(factory.createSemicolon(), body.rBrace)
            }

            klass.addDeclaration(declaration)
        }
    }

    return klass
}

context(_: KaSession)
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

    val isToKeepAbstract = isToKeepAbstract(mode, symbol)
    val body = if (bodyType != BodyType.NoBody && !isToKeepAbstract) {
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

context(_: KaSession)
private fun isToKeepAbstract(
    mode: MemberGenerateMode,
    symbol: KaCallableSymbol
): Boolean = mode != MemberGenerateMode.OVERRIDE && symbol.modality == KaSymbolModality.ABSTRACT

context(_: KaSession) @OptIn(KaExperimentalApi::class)
fun <T> generateUnsupportedOrSuperCall(
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
