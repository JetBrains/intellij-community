// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.quickDoc

import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.annotations.KaAnnotation
import org.jetbrains.kotlin.analysis.api.annotations.KaAnnotationValue
import org.jetbrains.kotlin.analysis.api.rendering.KaPiece
import org.jetbrains.kotlin.analysis.api.rendering.KaPieceRenderer
import org.jetbrains.kotlin.analysis.api.rendering.KaRenderer
import org.jetbrains.kotlin.analysis.api.rendering.KaRenderingContext
import org.jetbrains.kotlin.analysis.api.rendering.KaRenderingOption
import org.jetbrains.kotlin.analysis.api.rendering.KaRenderingOutput
import org.jetbrains.kotlin.analysis.api.rendering.KaTextAttribute
import org.jetbrains.kotlin.analysis.api.rendering.append
import org.jetbrains.kotlin.analysis.api.rendering.identifier
import org.jetbrains.kotlin.analysis.api.rendering.keyword
import org.jetbrains.kotlin.analysis.api.rendering.output
import org.jetbrains.kotlin.analysis.api.rendering.punctuation
import org.jetbrains.kotlin.analysis.api.rendering.push
import org.jetbrains.kotlin.analysis.api.rendering.pushEmpty
import org.jetbrains.kotlin.analysis.api.rendering.render
import org.jetbrains.kotlin.analysis.api.rendering.reset
import org.jetbrains.kotlin.analysis.api.symbols.KaAnonymousObjectSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaClassKind
import org.jetbrains.kotlin.analysis.api.symbols.KaConstructorSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaDeclarationSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaEnumEntrySymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaJavaFieldSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaLocalVariableSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaNamedClassSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaNamedFunctionSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaParameterSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaPropertySymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaSymbolModality
import org.jetbrains.kotlin.analysis.api.symbols.KaSymbolVisibility
import org.jetbrains.kotlin.analysis.api.symbols.KaTypeParameterSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaValueParameterSymbol
import org.jetbrains.kotlin.analysis.api.symbols.containingDeclaration
import org.jetbrains.kotlin.analysis.api.symbols.markers.KaNamedSymbol
import org.jetbrains.kotlin.analysis.api.types.KaClassType
import org.jetbrains.kotlin.analysis.api.types.expandedSymbol
import org.jetbrains.kotlin.analysis.api.types.type
import org.jetbrains.kotlin.descriptors.annotations.AnnotationUseSiteTarget
import org.jetbrains.kotlin.idea.base.analysis.api.utils.defaultValue
import org.jetbrains.kotlin.idea.codeinsights.impl.base.parameterInfo.KotlinParameterInfoBase
import org.jetbrains.kotlin.idea.highlighting.parameterInfo.KotlinIdeDescriptorRendererHighlightingManager
import org.jetbrains.kotlin.idea.highlighting.parameterInfo.KotlinIdeDescriptorRendererHighlightingManager.Companion.Attributes
import org.jetbrains.kotlin.idea.util.renderSymbolName
import org.jetbrains.kotlin.idea.util.withAlwaysRenderedReturnType
import org.jetbrains.kotlin.idea.util.withImplicitVisibility
import org.jetbrains.kotlin.idea.util.withShortNames
import org.jetbrains.kotlin.lexer.KtModifierKeywordToken
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.name.SpecialNames
import org.jetbrains.kotlin.name.StandardClassIds
import org.jetbrains.kotlin.name.render
import org.jetbrains.kotlin.psi.KtAnnotationEntry
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtNameReferenceExpression
import org.jetbrains.kotlin.psi.KtParameter
import org.jetbrains.kotlin.psi.addRemoveModifier.sortModifiers

internal class KotlinIdeDeclarationRenderer(private val highlighting: KotlinIdeDescriptorRendererHighlightingManager<Attributes>) {
    context(session: KaSession)
    fun render(symbol: KaDeclarationSymbol): String {
        val output = KotlinQuickDocRenderingOutput(session, highlighting)
        kotlinQuickDocRenderer.render(symbol, output)
        return output.toString()
    }

    context(session: KaSession)
    fun renderFunctionTypeParameter(parameter: KtParameter): String {
        val output = KotlinQuickDocRenderingOutput(session, highlighting)
        parameter.nameAsName?.let { name ->
            output.append(name.render(), KotlinQuickDocTextAttribute.PARAMETER_LABEL)
            output.append(":", KaTextAttribute.Punctuation).space()
        }
        parameter.typeReference?.type?.let { kotlinQuickDocRenderer.render(it, output) }
        return output.toString()
    }
}

private val kotlinQuickDocRenderer: KaRenderer = KaRenderer.withShortNames.copy {
    withImplicitVisibility()
    withAlwaysRenderedReturnType()
    set(KaRenderingOption.LinkSymbols, true)
    set(KaRenderingOption.MultilineValueParameterLists, true)
    set(KaRenderingOption.TypeTransformation) { it }
    reset(KaRenderingOption.Modifiers) { previous ->
        { symbol, modifiers -> documentationModifiers(symbol, previous(symbol, modifiers)) }
    }

    pushEmpty(KaPiece.ClassBody)
    pushEmpty(KaPiece.PropertyAccessors)
    pushEmpty(KaPiece.SamConstructor)
    push(KaPiece.Supertype) { render(it, KaPiece.Type) }
    push(KaPiece.DefinitelyNotNullType) { type ->
        render(type.original, KaPiece.Type)
        output.space().punctuation("&").space()
        render(StandardClassIds.Any, KaPiece.ClassName)
    }
    push(KaPiece.PrimaryConstructor) { constructor ->
        if ((constructor.containingDeclaration as? KaNamedClassSymbol)?.isData == true) {
            render(constructor, KaPiece.ValueParameterList)
        }
    }
    push(KaPiece.Constructor) { constructor ->
        render(constructor to null, KaPiece.Annotations)
        render(constructor, KaPiece.SymbolModifiers)
        output.keyword(KtTokens.CONSTRUCTOR_KEYWORD)
        renderSymbolName(constructor.containingDeclaration)
        render(constructor, KaPiece.ValueParameterList)
    }
    push(LocalModifier)
    push(DeclarationName)
    push(DefaultCompanion)
    push(ConstructorPropertyParameter)
    push(ParameterDefault)
    push(PropertyInitializer)
    push(FieldAnnotations)
    push(SourceAnnotation)
    push(QuotedAnnotationArguments)
    push(QuotedEnumEntry)
    push(AnonymousObjectType)
}

/** Includes implicit visibility and modality, and preserves the source spelling of an inline class. */
context(_: KaSession)
private fun documentationModifiers(symbol: KaDeclarationSymbol, modifiers: List<KtModifierKeywordToken>): List<KtModifierKeywordToken> {
    val result = modifiers.toMutableSet()
    val isOverride = symbol is KaNamedFunctionSymbol && symbol.isOverride || symbol is KaPropertySymbol && symbol.isOverride
    if (isOverride || symbol is KaNamedClassSymbol && symbol.isInline) {
        result.retainAll { it in KtTokens.VISIBILITY_MODIFIERS }
        if (symbol is KaNamedFunctionSymbol && symbol.isSuspend) result.add(KtTokens.SUSPEND_KEYWORD)
        val klass = symbol.psi as? KtClass
        if (klass?.hasModifier(KtTokens.INLINE_KEYWORD) == true) result.add(KtTokens.INLINE_KEYWORD)
        if (klass?.hasModifier(KtTokens.VALUE_KEYWORD) == true) result.add(KtTokens.VALUE_KEYWORD)
    }
    if (symbol is KaConstructorSymbol) {
        if (symbol.isActual) result.add(KtTokens.ACTUAL_KEYWORD)
        if (symbol.isExpect) result.add(KtTokens.EXPECT_KEYWORD)
        when (symbol.visibility) {
            KaSymbolVisibility.PRIVATE -> result.add(KtTokens.PRIVATE_KEYWORD)
            KaSymbolVisibility.PROTECTED -> result.add(KtTokens.PROTECTED_KEYWORD)
            else -> Unit
        }
    }
    val showModality = when (symbol) {
        is KaNamedClassSymbol -> !(symbol.classKind == KaClassKind.INTERFACE && symbol.modality == KaSymbolModality.ABSTRACT ||
                symbol.classKind.isObject && symbol.modality == KaSymbolModality.FINAL)
        is KaNamedFunctionSymbol, is KaPropertySymbol, is KaJavaFieldSymbol ->
            symbol.modality != KaSymbolModality.FINAL || symbol.containingDeclaration != null
        else -> false
    }
    if (showModality) {
        result.add(when (symbol.modality) {
            KaSymbolModality.FINAL -> KtTokens.FINAL_KEYWORD
            KaSymbolModality.OPEN -> KtTokens.OPEN_KEYWORD
            KaSymbolModality.SEALED -> KtTokens.SEALED_KEYWORD
            KaSymbolModality.ABSTRACT -> KtTokens.ABSTRACT_KEYWORD
        })
    }
    return sortModifiers(result.toList())
}

/** Labels named local declarations. */
private object LocalModifier : KaPieceRenderer<KaDeclarationSymbol>(KaPiece.SymbolModifiers) {
    context(session: KaSession, context: KaRenderingContext, output: KaRenderingOutput)
    override fun render(value: KaDeclarationSymbol, next: () -> Unit): Boolean {
        if (value !is KaNamedSymbol || value is KaTypeParameterSymbol || value.visibility != KaSymbolVisibility.LOCAL) return false
        output.append("local", KaTextAttribute.Keyword).space()
        next()
        return true
    }
}

/** Labels enum entries and displays special parameter and variable names without their markers. */
private object DeclarationName : KaPieceRenderer<KaNamedSymbol>(KaPiece.SymbolName) {
    context(session: KaSession, context: KaRenderingContext, output: KaRenderingOutput)
    override fun render(value: KaNamedSymbol, next: () -> Unit): Boolean {
        if (value is KaEnumEntrySymbol && context.isInside(KaPiece.EnumEntry)) {
            output.append("enum entry", KaTextAttribute.Keyword).space()
            next()
            return true
        }
        val name = value.name
        if (!name.isSpecial || name == SpecialNames.UNDERSCORE_FOR_UNUSED_VAR) return false
        val text = name.asStringStripSpecialMarkers()
        if (value is KaParameterSymbol || value is KaLocalVariableSymbol) {
            output.append(text, KotlinQuickDocTextAttribute.INFORMATION)
        } else {
            output.identifier(text, value)
        }
        return true
    }
}

/** Names the containing class of a default companion object. */
private object DefaultCompanion : KaPieceRenderer<KaNamedClassSymbol>(KaPiece.NamedClass) {
    context(session: KaSession, context: KaRenderingContext, output: KaRenderingOutput)
    override fun render(value: KaNamedClassSymbol, next: () -> Unit): Boolean {
        if (value.classKind != KaClassKind.COMPANION_OBJECT || value.name != SpecialNames.DEFAULT_NAME_FOR_COMPANION_OBJECT) return false
        render(value, KaPiece.ClassAnnotations)
        render(value, KaPiece.SymbolModifiers)
        output.keyword(KtTokens.OBJECT_KEYWORD)
        value.containingDeclaration?.let {
            output.append("of", KotlinQuickDocTextAttribute.INFORMATION).space()
            renderSymbolName(it)
        }
        render(value, KaPiece.SupertypeList)
        return true
    }
}

/** Shows the property type and a single default value for a standalone constructor parameter. */
private object ConstructorPropertyParameter : KaPieceRenderer<KaValueParameterSymbol>(KaPiece.ValueParameter) {
    context(session: KaSession, context: KaRenderingContext, output: KaRenderingOutput)
    override fun render(value: KaValueParameterSymbol, next: () -> Unit): Boolean {
        if (context.isInside(KaPiece.ValueParameterList)) return false
        val property = value.primaryConstructorProperty ?: return false
        render(property, KaPiece.Property)
        render(value, KaPiece.ValueParameterDefaultValue)
        return true
    }
}

/** Shows the source expression and the value of a referenced constant. */
private object ParameterDefault : KaPieceRenderer<KaValueParameterSymbol>(KaPiece.ValueParameterDefaultValueExpression) {
    context(session: KaSession, context: KaRenderingContext, output: KaRenderingOutput)
    override fun render(value: KaValueParameterSymbol, next: () -> Unit): Boolean {
        val expression = value.defaultValue?.takeUnless { it.containingKtFile.isCompiled } ?: return false
        val (text, isConstValue) = KotlinParameterInfoBase.getDefaultValueStringRepresentation(expression)
        if (expression is KtNameReferenceExpression && isConstValue) {
            output.append(expression.text, KotlinQuickDocTextAttribute.PARAMETER_LABEL)
            output.space().punctuation("=").space()
        }
        output.append(text, KotlinQuickDocTextAttribute.CODE_SNIPPET)
        return true
    }
}

/** Shows a source initializer for a property declared outside a parameter list. */
private object PropertyInitializer : KaPieceRenderer<KaPropertySymbol>(KaPiece.PropertyInitializer) {
    context(session: KaSession, context: KaRenderingContext, output: KaRenderingOutput)
    override fun render(value: KaPropertySymbol, next: () -> Unit): Boolean {
        if (context.isInside(KaPiece.ValueParameter)) return true
        value.initializer?.initializerPsi?.let { expression ->
            output.space().punctuation("=").space()
            output.append(KotlinParameterInfoBase.getDefaultValueStringRepresentation(expression).text, KotlinQuickDocTextAttribute.CODE_SNIPPET)
        }
        return true
    }
}

/** Adds annotations of an explicit backing field to the property header. */
private object FieldAnnotations : KaPieceRenderer<KaPropertySymbol>(KaPiece.PropertyAnnotations) {
    context(session: KaSession, context: KaRenderingContext, output: KaRenderingOutput)
    override fun render(value: KaPropertySymbol, next: () -> Unit): Boolean {
        val field = value.backingFieldSymbol?.takeIf { it.isNotDefault } ?: return false
        next()
        val target = if (value.isDelegated) AnnotationUseSiteTarget.PROPERTY_DELEGATE_FIELD else AnnotationUseSiteTarget.FIELD
        val onNewLine = context.valueFor(KaRenderingOption.AnnotationsOnNewLine)(value)
        for (annotation in context.valueFor(KaRenderingOption.Annotations)(field)) {
            if (annotation.psi != null && value.annotations.any { it.psi === annotation.psi }) continue
            render(annotation to target, KaPiece.Annotation)
            if (onNewLine) output.newLine() else output.space()
        }
        return true
    }
}

/** Preserves a written use-site target and marks an unresolved annotation name. */
private object SourceAnnotation : KaPieceRenderer<Pair<KaAnnotation, AnnotationUseSiteTarget?>>(KaPiece.Annotation) {
    context(session: KaSession, context: KaRenderingContext, output: KaRenderingOutput)
    override fun render(value: Pair<KaAnnotation, AnnotationUseSiteTarget?>, next: () -> Unit): Boolean {
        val (annotation, target) = value
        if (target == null) {
            val written = (annotation.psi as? KtAnnotationEntry)?.useSiteTarget?.getAnnotationUseSiteTarget()
            if (written != null) {
                render(annotation to written, KaPiece.Annotation)
                return true
            }
        }
        val name = annotation.classId?.shortClassName
        if (name != null && !name.isSpecial) return false
        output.punctuation("@")
        if (target != null) output.append(target.renderName, KaTextAttribute.Keyword).punctuation(":")
        output.append(name?.render() ?: "ERROR_ANNOTATION", KotlinQuickDocTextAttribute.ERROR)
        render(annotation, KaPiece.AnnotationValues)
        return true
    }
}

/** Keeps backticks around annotation argument names that need them. */
private object QuotedAnnotationArguments : KaPieceRenderer<KaAnnotation>(KaPiece.AnnotationValues) {
    context(session: KaSession, context: KaRenderingContext, output: KaRenderingOutput)
    override fun render(value: KaAnnotation, next: () -> Unit): Boolean {
        if (value.arguments.none { it.name.render() != it.name.asString() }) return false
        output.punctuation("(")
        value.arguments.forEachIndexed { index, argument ->
            if (index > 0) output.punctuation(",").space()
            output.append(argument.name.render(), KaTextAttribute.Identifier).space().punctuation("=").space()
            render(argument.expression, KaPiece.AnnotationValue)
        }
        output.punctuation(")")
        return true
    }
}

/** Keeps backticks around enum entry names that need them. */
private object QuotedEnumEntry : KaPieceRenderer<KaAnnotationValue>(KaPiece.AnnotationValue) {
    context(session: KaSession, context: KaRenderingContext, output: KaRenderingOutput)
    override fun render(value: KaAnnotationValue, next: () -> Unit): Boolean {
        val id = (value as? KaAnnotationValue.EnumEntryValue)?.callableId ?: return false
        val name = id.callableName
        if (name.render() == name.asString()) return false
        id.classId?.let {
            render(it, KaPiece.ClassName)
            output.punctuation(".")
        }
        output.append(name.render(), KaTextAttribute.Identifier)
        return true
    }
}

/** Describes an anonymous object by its supertypes. */
private object AnonymousObjectType : KaPieceRenderer<KaClassType>(KaPiece.ClassTypeName) {
    context(session: KaSession, context: KaRenderingContext, output: KaRenderingOutput)
    override fun render(value: KaClassType, next: () -> Unit): Boolean {
        val symbol = value.expandedSymbol as? KaAnonymousObjectSymbol ?: return false
        output.append("<anonymous object : ")
        symbol.superTypes.forEachIndexed { index, type ->
            if (index > 0) output.punctuation(",").space()
            render(type, KaPiece.Type)
        }
        output.append(">")
        return true
    }
}