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
import com.intellij.ui.SimpleTextAttributes
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.kotlin.analysis.api.KaExperimentalApi
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.annotations.KaAnnotation
import org.jetbrains.kotlin.analysis.api.rendering.KaPiece
import org.jetbrains.kotlin.analysis.api.rendering.KaPieceRenderer
import org.jetbrains.kotlin.analysis.api.rendering.KaRenderer
import org.jetbrains.kotlin.analysis.api.rendering.KaRendererBuilder
import org.jetbrains.kotlin.analysis.api.rendering.KaRenderingContext
import org.jetbrains.kotlin.analysis.api.rendering.KaRenderingOption
import org.jetbrains.kotlin.analysis.api.rendering.KaRenderingOutput
import org.jetbrains.kotlin.analysis.api.rendering.KaTextAttribute
import org.jetbrains.kotlin.analysis.api.rendering.append
import org.jetbrains.kotlin.analysis.api.rendering.push
import org.jetbrains.kotlin.analysis.api.rendering.pushEmpty
import org.jetbrains.kotlin.analysis.api.rendering.render
import org.jetbrains.kotlin.analysis.api.rendering.renderToString
import org.jetbrains.kotlin.analysis.api.scopes.combinedDeclaredMemberScope
import org.jetbrains.kotlin.analysis.api.scopes.declaredMemberScope
import org.jetbrains.kotlin.analysis.api.symbols.KaCallableSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaClassKind
import org.jetbrains.kotlin.analysis.api.symbols.KaClassSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaConstructorSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaDeclarationSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaEnumEntrySymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaFunctionSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaNamedClassSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaNamedFunctionSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaPropertySymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaSymbolModality
import org.jetbrains.kotlin.analysis.api.symbols.KaSymbolOrigin
import org.jetbrains.kotlin.analysis.api.symbols.KaSymbolVisibility
import org.jetbrains.kotlin.analysis.api.symbols.KaTypeAliasSymbol
import org.jetbrains.kotlin.analysis.api.symbols.containingSymbol
import org.jetbrains.kotlin.analysis.api.symbols.fakeOverrideOriginal
import org.jetbrains.kotlin.analysis.api.symbols.findClass
import org.jetbrains.kotlin.analysis.api.symbols.getExpectsForActual
import org.jetbrains.kotlin.analysis.api.symbols.isSubClassOf
import org.jetbrains.kotlin.analysis.api.symbols.name
import org.jetbrains.kotlin.analysis.api.symbols.pointers.KaSymbolPointer
import org.jetbrains.kotlin.analysis.api.symbols.symbol
import org.jetbrains.kotlin.analysis.api.types.KaStandardTypeClassIds
import org.jetbrains.kotlin.analysis.api.types.classId
import org.jetbrains.kotlin.idea.base.psi.addMemberDeclaration
import org.jetbrains.kotlin.idea.base.psi.addModifierKeyword
import org.jetbrains.kotlin.idea.base.psi.getOrCreateClassBody
import org.jetbrains.kotlin.idea.base.psi.getOrCreatePrimaryConstructor
import org.jetbrains.kotlin.idea.base.util.names.FqNames.OptInFqNames.isRequiresOptInFqName
import org.jetbrains.kotlin.idea.core.TemplateKind
import org.jetbrains.kotlin.idea.core.getFunctionBodyTextFromTemplate
import org.jetbrains.kotlin.idea.j2k.IdeaDocCommentConverter
import org.jetbrains.kotlin.idea.kdoc.KDocElementFactory
import org.jetbrains.kotlin.idea.util.withPsiTextForErrorReturnType
import org.jetbrains.kotlin.idea.util.withQualifiedDenotableSupertypes
import org.jetbrains.kotlin.idea.util.withJavaSourceParameterNames
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.name.render
import org.jetbrains.kotlin.psi.KtCallableDeclaration
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.KtDeclaration
import org.jetbrains.kotlin.psi.KtEnumEntry
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtModifierListOwner
import org.jetbrains.kotlin.psi.KtParameter
import org.jetbrains.kotlin.psi.KtPrimaryConstructor
import org.jetbrains.kotlin.psi.KtPropertyAccessor
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.psi.KtPsiUtil
import org.jetbrains.kotlin.psi.KtTypeParameter
import org.jetbrains.kotlin.psi.addRemoveModifier.sortModifiers
import org.jetbrains.kotlin.psi.findDocComment.findDocComment
import org.jetbrains.kotlin.psi.psiUtil.forEachDescendantOfType
import org.jetbrains.kotlin.psi.psiUtil.hasActualModifier
import org.jetbrains.kotlin.psi.psiUtil.hasExpectModifier
import org.jetbrains.kotlin.psi.psiUtil.hasValueModifier
import org.jetbrains.kotlin.psi.psiUtil.isPropertyParameter
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
    val isDeprecated: Boolean,
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
            isDeprecated = (symbol.psi as? KtModifierListOwner)?.let(KtPsiUtil::isDeprecated) ?: false,
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
    private val isDeprecated: Boolean = memberInfo.isDeprecated

    override fun getParentNodeDelegate(): MemberChooserObject? = memberInfo.containingSymbolText?.let {
        KaClassOrObjectSymbolChooserObject(
            memberInfo.containingSymbolText,
            memberInfo.containingSymbolIcon
        )
    }

    override fun getTextStyle(): Int =
        if (isDeprecated) SimpleTextAttributes.STYLE_STRIKEOUT else SimpleTextAttributes.STYLE_PLAIN
}

data class KaClassOrObjectSymbolChooserObject(
    @NlsContexts.Label val symbolText: String?,
    val symbolIcon: Icon?
) :
    MemberChooserObjectBase(symbolText, symbolIcon)

@ApiStatus.Internal
fun createKtClassMember(
    memberInfo: KtClassMemberInfo,
    bodyType: BodyType,
    preferConstructorParameter: Boolean
): KtClassMember = KtClassMember(memberInfo, bodyType, preferConstructorParameter)

@KaExperimentalApi
@ApiStatus.Internal
context(_: KaSession)
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
        MemberGenerateMode.ACTUAL -> newMember.addModifierKeyword(KtTokens.ACTUAL_KEYWORD)
        MemberGenerateMode.EXPECT -> if (targetClass == null) {
            newMember.addModifierKeyword(KtTokens.EXPECT_KEYWORD)
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

@KaExperimentalApi
@ApiStatus.Internal
context(_: KaSession)
fun generateClassWithMembers(
    project: Project,
    symbol: KaClassSymbol,
    targetClass: KtClassOrObject?,
    mode: MemberGenerateMode = MemberGenerateMode.OVERRIDE
): KtClassOrObject {
    val renderer = createRenderer(targetClass, mode, symbol)

    val newClass = generateClass(project, symbol, renderer, mode)

    when (mode) {
        MemberGenerateMode.ACTUAL -> {
            newClass.addModifierKeyword(KtTokens.ACTUAL_KEYWORD)
            newClass.forEachDescendantOfType<KtDeclaration> { declaration ->
                if (!(declaration is KtParameter && !declaration.isPropertyParameter()) &&
                    declaration !is KtPropertyAccessor &&
                    declaration !is KtTypeParameter &&
                    declaration !is KtEnumEntry) {
                    declaration.addModifierKeyword(KtTokens.ACTUAL_KEYWORD)
                }
            }

            val primaryConstructor = if ((symbol.psi as? KtClass)?.primaryConstructor != null) {
                // renderer skips constructors without parameters/annotations
                // though they are required to have explicit actual modifier
                (newClass as? KtClass)?.getOrCreatePrimaryConstructor()
            } else {
                newClass.primaryConstructor
            }
            primaryConstructor?.addModifierKeyword(KtTokens.ACTUAL_KEYWORD)
        }
        MemberGenerateMode.EXPECT -> {
            if (targetClass == null) {
                newClass.addModifierKeyword(KtTokens.EXPECT_KEYWORD)
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

/**
 * The modifiers which the override/implement generator never copies from the member being overridden: an override does not inherit the
 * visibility, and `operator`, `infix` and `lateinit` are properties of the original declaration rather than of the override.
 */
private val MODIFIERS_NOT_INHERITED_BY_OVERRIDE: TokenSet = TokenSet.orSet(
    KtTokens.VISIBILITY_MODIFIERS,
    TokenSet.create(KtTokens.OPERATOR_KEYWORD, KtTokens.INFIX_KEYWORD, KtTokens.LATEINIT_KEYWORD),
)

/**
 * `expect` and `actual` are never taken from the rendered symbol: [generateMember] and [generateClassWithMembers] add them to the generated
 * PSI according to the [MemberGenerateMode] instead.
 */
private val EXPECT_ACTUAL_MODIFIERS: TokenSet = TokenSet.create(KtTokens.EXPECT_KEYWORD, KtTokens.ACTUAL_KEYWORD)

/**
 * Renders `: this()` after a secondary constructor whose class has a primary constructor, which such a constructor has to delegate to.
 *
 * The delegation call takes the place of the constructor body, which is where [KaPiece.ConstructorBody] sits: right after the value
 * parameter list.
 */
private class SecondaryConstructorDelegationRenderer(
    private val mode: MemberGenerateMode,
) : KaPieceRenderer<KaConstructorSymbol>(KaPiece.ConstructorBody) {
    context(session: KaSession, context: KaRenderingContext, output: KaRenderingOutput)
    override fun render(value: KaConstructorSymbol, next: () -> Unit): Boolean {
        if (value.isPrimary || !delegatesToPrimaryConstructor(value)) {
            return false
        }

        output.append(" : this()", KaTextAttribute.Punctuation)
        return true
    }

    context(_: KaSession)
    private fun delegatesToPrimaryConstructor(symbol: KaConstructorSymbol): Boolean {
        val containingClass = symbol.containingSymbol as? KaClassSymbol ?: return false
        return containingClass.declaredMemberScope.constructors.any {
            it.isPrimary && (mode == MemberGenerateMode.ACTUAL || mode == MemberGenerateMode.EXPECT && it.isActual)
        }
    }
}

/**
 * Renders the primary constructor in the class header as source code.
 *
 * The `constructor` keyword is written only when the constructor carries an annotation or a visibility which is written in source code: an
 * enum class, an object and a sealed class declare the visibility of their primary constructor implicitly. Once the keyword is written the
 * parentheses become mandatory, even for a constructor without parameters.
 */
private object PrimaryConstructorInHeaderRenderer : KaPieceRenderer<KaConstructorSymbol>(KaPiece.PrimaryConstructor) {
    context(session: KaSession, context: KaRenderingContext, output: KaRenderingOutput)
    override fun render(value: KaConstructorSymbol, next: () -> Unit): Boolean {
        if (!hasWrittenAnnotationsOrVisibility(value)) {
            if (value.valueParameters.isNotEmpty()) {
                render(value, KaPiece.ValueParameterList)
            }
            return true
        }

        output.space()
        render(value to null, KaPiece.Annotations)
        render(value, KaPiece.SymbolModifiers)
        output.append(KtTokens.CONSTRUCTOR_KEYWORD.value, KaTextAttribute.Keyword)
        render(value, KaPiece.ValueParameterList)
        return true
    }

    context(_: KaSession)
    private fun hasWrittenAnnotationsOrVisibility(symbol: KaConstructorSymbol): Boolean {
        if (symbol.annotations.isNotEmpty()) return true
        if (symbol.visibility == KaSymbolVisibility.PUBLIC) return false

        val containingClass = symbol.containingSymbol as? KaClassSymbol ?: return true
        return containingClass.classKind != KaClassKind.ENUM_CLASS &&
                !containingClass.classKind.isObject &&
                containingClass.modality != KaSymbolModality.SEALED
    }
}

context(_: KaSession)
private fun createRenderer(
    targetClass: KtClassOrObject?,
    mode: MemberGenerateMode,
    topDeclarationSymbol: KaDeclarationSymbol
): KaRenderer {
    val containingSymbol = targetClass?.symbol as? KaClassSymbol

    return KaRenderer.withQualifiedDenotableSupertypes.copy {
        withJavaSourceParameterNames()
        withPsiTextForErrorReturnType()
        push(SecondaryConstructorDelegationRenderer(mode))
        push(PrimaryConstructorInHeaderRenderer)
        pushEmpty(KaPiece.ValueParameterDefaultValue)
        pushEmpty(KaPiece.ClassBody)
        pushEmpty(KaPiece.PropertyInitializer)
        pushEmpty(KaPiece.PropertyAccessors)

        if (mode == MemberGenerateMode.OVERRIDE) {
            withOverridableAnnotationsOnly(targetClass?.containingKtFile)
        }

        if (mode == MemberGenerateMode.EXPECT) {
            // An `expect` supertype list carries no constructor call.
            push(KaPiece.Supertype) { render(it, KaPiece.Type) }
        }

        set(KaRenderingOption.Modifiers) { symbol, modifiers ->
            buildList {
                addAll(modifiers)
                removeAll { it in EXPECT_ACTUAL_MODIFIERS }

                when (mode) {
                    MemberGenerateMode.OVERRIDE -> {
                        removeAll { it in MODIFIERS_NOT_INHERITED_BY_OVERRIDE }

                        if (symbol == topDeclarationSymbol) {
                            // The generated member is an override, and takes its modality from the class it is generated into.
                            removeAll { it in KtTokens.MODALITY_MODIFIERS }
                            add(KtTokens.OVERRIDE_KEYWORD)
                        }

                        if (containingSymbol?.isActual == true && shouldBeActual(symbol, containingSymbol)) {
                            add(KtTokens.ACTUAL_KEYWORD)
                        }
                    }

                    // An `expect` class declares no `data`, since its members are not generated.
                    MemberGenerateMode.EXPECT -> remove(KtTokens.DATA_KEYWORD)

                    MemberGenerateMode.ACTUAL -> {}
                }

                if (usesInlineInsteadOfValueModifier(symbol)) {
                    remove(KtTokens.VALUE_KEYWORD)
                    add(KtTokens.INLINE_KEYWORD)
                }
            }.distinct().let(::sortModifiers)
        }
    }
}

/**
 * Renders only those annotations of a declaration which an override has to carry over: the opt-in markers, plus whatever an
 * [OverrideImplementsAnnotationsFilter] asks to keep for [file].
 *
 * Annotations on types are left alone, as they are part of the type the override has to repeat.
 */
private fun KaRendererBuilder.withOverridableAnnotationsOnly(file: KtFile?) {
    val previousValue = get(KaRenderingOption.Annotations)
    set(KaRenderingOption.Annotations) { annotated ->
        val annotations = previousValue(annotated)
        if (annotated is KaDeclarationSymbol) annotations.filter { keepAnnotation(it, file) } else annotations
    }
}

/**
 * Whether the generated member needs an explicit `actual` modifier because the class it is generated into is itself `actual`.
 */
context(_: KaSession)
private fun shouldBeActual(symbol: KaDeclarationSymbol, containingSymbol: KaClassSymbol): Boolean {
    if (symbol.isActual) return true

    val containingInterface = symbol.containingSymbol
    return containingInterface is KaClassSymbol &&
            containingSymbol.getExpectsForActual().any { (it as? KaClassSymbol)?.isSubClassOf(containingInterface) == true }
}

/**
 * Whether an inline class is declared with the old `inline` modifier rather than with `value`. The symbol reports both the same way, so the
 * source declaration decides.
 */
private fun usesInlineInsteadOfValueModifier(symbol: KaDeclarationSymbol): Boolean =
    symbol is KaNamedClassSymbol &&
            symbol.isInline &&
            (symbol.psi as? KtClassOrObject)?.modifierList?.hasValueModifier() != true

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
private fun generateConstructorParameter(
    project: Project,
    symbol: KaCallableSymbol,
    renderer: KaRenderer,
): KtCallableDeclaration {
    return KtPsiFactory(project).createParameter(renderer.renderToString(symbol))
}

context(_: KaSession)
private fun generateFunction(
    project: Project,
    symbol: KaFunctionSymbol,
    renderer: KaRenderer,
    mode: MemberGenerateMode,
    bodyType: BodyType,
): KtCallableDeclaration {
    val returnType = symbol.returnType
    val returnsUnit = returnType.classId == KaStandardTypeClassIds.UNIT

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
    val functionText = renderer.renderToString(symbol) + body

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
private fun generateClass(
    project: Project,
    symbol: KaClassSymbol,
    renderer: KaRenderer,
    mode: MemberGenerateMode,
): KtClassOrObject {
    val factory = KtPsiFactory(project)
    val classText = renderer.renderToString(symbol)
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
                KtPsiFactory(project).createEnumEntry(renderer.renderToString(declaration))
            }
            is KaTypeAliasSymbol -> {
                // Skip nested type aliases: expect/actual nested type aliases are forbidden
                null
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
                val body = klass.getOrCreateClassBody()
                body.addBefore(factory.createSemicolon(), body.rBrace)
            }

            klass.addMemberDeclaration(declaration)
        }
    }

    return klass
}

context(_: KaSession)
private fun generateProperty(
    project: Project,
    symbol: KaPropertySymbol,
    renderer: KaRenderer,
    mode: MemberGenerateMode,
    bodyType: BodyType,
): KtCallableDeclaration {
    val returnType = symbol.returnType
    val returnsNotUnit = returnType.classId != KaStandardTypeClassIds.UNIT

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
    return KtPsiFactory(project).createProperty(renderer.renderToString(symbol) + body)
}

context(_: KaSession)
private fun isToKeepAbstract(
    mode: MemberGenerateMode,
    symbol: KaCallableSymbol
): Boolean = mode != MemberGenerateMode.OVERRIDE && symbol.modality == KaSymbolModality.ABSTRACT

context(_: KaSession)
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
                project,
                templateKind,
                name = symbol.name?.asString(),
                returnType = KaRenderer.withQualifiedDenotableSupertypes.renderToString(symbol.returnType),
                classFqName = null
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
