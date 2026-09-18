// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.util

import com.intellij.psi.tree.TokenSet
import org.jetbrains.kotlin.analysis.api.KaExperimentalApi
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.annotations.KaAnnotationValue
import org.jetbrains.kotlin.analysis.api.rendering.KaClassTypeQualification
import org.jetbrains.kotlin.analysis.api.rendering.KaClassTypeRenderingMode
import org.jetbrains.kotlin.analysis.api.rendering.KaPiece
import org.jetbrains.kotlin.analysis.api.rendering.KaPieceRenderer
import org.jetbrains.kotlin.analysis.api.rendering.KaRenderer
import org.jetbrains.kotlin.analysis.api.rendering.KaRendererBuilder
import org.jetbrains.kotlin.analysis.api.rendering.KaRenderingContext
import org.jetbrains.kotlin.analysis.api.rendering.KaRenderingOption
import org.jetbrains.kotlin.analysis.api.rendering.KaRenderingOutput
import org.jetbrains.kotlin.analysis.api.rendering.KaTextAttribute
import org.jetbrains.kotlin.analysis.api.rendering.KaTypeApproximation
import org.jetbrains.kotlin.analysis.api.rendering.append
import org.jetbrains.kotlin.analysis.api.rendering.output
import org.jetbrains.kotlin.analysis.api.rendering.push
import org.jetbrains.kotlin.analysis.api.rendering.pushEmpty
import org.jetbrains.kotlin.analysis.api.rendering.render
import org.jetbrains.kotlin.analysis.api.rendering.renderToString
import org.jetbrains.kotlin.analysis.api.scopes.staticDeclaredMemberScope
import org.jetbrains.kotlin.analysis.api.symbols.KaAnonymousFunctionSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaAnonymousObjectSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaCallableSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaClassInitializerSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaClassSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaDeclarationSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaNamedClassSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaParameterSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaScriptSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaSymbolVisibility
import org.jetbrains.kotlin.analysis.api.symbols.KaTypeParameterSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaValueParameterSymbol
import org.jetbrains.kotlin.analysis.api.symbols.containingDeclaration
import org.jetbrains.kotlin.analysis.api.symbols.fakeOverrideOriginal
import org.jetbrains.kotlin.analysis.api.symbols.findClass
import org.jetbrains.kotlin.analysis.api.symbols.markers.KaNamedSymbol
import org.jetbrains.kotlin.analysis.api.types.KaClassType
import org.jetbrains.kotlin.analysis.api.types.KaErrorType
import org.jetbrains.kotlin.analysis.api.types.KaFunctionType
import org.jetbrains.kotlin.analysis.api.types.KaStarTypeProjection
import org.jetbrains.kotlin.analysis.api.types.KaType
import org.jetbrains.kotlin.analysis.api.types.KaTypeArgumentWithVariance
import org.jetbrains.kotlin.lexer.KtKeywordToken
import org.jetbrains.kotlin.lexer.KtModifierKeywordToken
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.load.java.NULLABILITY_ANNOTATIONS
import org.jetbrains.kotlin.name.CallableId
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.name.SpecialNames
import org.jetbrains.kotlin.name.render
import org.jetbrains.kotlin.psi.KtCallableDeclaration
import org.jetbrains.kotlin.psi.addRemoveModifier.sortModifiers
import org.jetbrains.kotlin.types.Variance
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.contract

@KaExperimentalApi
private fun sourceRenderer(
    qualification: KaClassTypeQualification,
    approximation: KaTypeApproximation = KaTypeApproximation.NONE,
): KaRenderer = KaRenderer.default.copy {
    withoutNullabilityAnnotations()
    set(KaRenderingOption.ClassTypeQualification, qualification)
    set(KaRenderingOption.TypeApproximation, approximation)
}

/**
 * Renders a function type without its parameter names, e.g. `(Int) -> Int` rather than `(n: Int) -> Int`.
 */
@KaExperimentalApi
fun KaRendererBuilder.withoutFunctionTypeParameterNames() {
    push(KaPiece.FunctionTypeParameter) { render(it.type, KaPiece.Type) }
}

@KaExperimentalApi
private val FQN_RENDERER: KaRenderer = sourceRenderer(KaClassTypeQualification.FULLY_QUALIFIED)

@KaExperimentalApi
private val FQN_DENOTABLE_SUPERTYPE_RENDERER: KaRenderer =
    sourceRenderer(KaClassTypeQualification.FULLY_QUALIFIED, KaTypeApproximation.TO_DENOTABLE_SUPERTYPE)

@KaExperimentalApi
private val FQN_DENOTABLE_SUBTYPE_RENDERER: KaRenderer =
    sourceRenderer(KaClassTypeQualification.FULLY_QUALIFIED, KaTypeApproximation.TO_DENOTABLE_SUBTYPE)

@KaExperimentalApi
private val SHORT_NAME_RENDERER: KaRenderer = sourceRenderer(KaClassTypeQualification.WITH_OUTER_CLASSIFIERS)

@KaExperimentalApi
private val SHORT_DENOTABLE_SUPERTYPE_RENDERER: KaRenderer =
    sourceRenderer(KaClassTypeQualification.WITH_OUTER_CLASSIFIERS, KaTypeApproximation.TO_DENOTABLE_SUPERTYPE)

@KaExperimentalApi
private val SHORT_DENOTABLE_SUBTYPE_RENDERER: KaRenderer =
    sourceRenderer(KaClassTypeQualification.WITH_OUTER_CLASSIFIERS, KaTypeApproximation.TO_DENOTABLE_SUBTYPE)

@KaExperimentalApi
val KaRenderer.Companion.withQualifiedNames: KaRenderer
    get() = FQN_RENDERER

@KaExperimentalApi
val KaRenderer.Companion.withQualifiedDenotableSupertypes: KaRenderer
    get() = FQN_DENOTABLE_SUPERTYPE_RENDERER

@KaExperimentalApi
val KaRenderer.Companion.withQualifiedDenotableSubtypes: KaRenderer
    get() = FQN_DENOTABLE_SUBTYPE_RENDERER

@KaExperimentalApi
val KaRenderer.Companion.withShortNames: KaRenderer
    get() = SHORT_NAME_RENDERER

@KaExperimentalApi
val KaRenderer.Companion.withShortDenotableSupertypes: KaRenderer
    get() = SHORT_DENOTABLE_SUPERTYPE_RENDERER

@KaExperimentalApi
val KaRenderer.Companion.withShortDenotableSubtypes: KaRenderer
    get() = SHORT_DENOTABLE_SUBTYPE_RENDERER

/** Disables rendering for nullability annotations */
@KaExperimentalApi
fun KaRendererBuilder.withoutNullabilityAnnotations() {
    val previousValue = get(KaRenderingOption.Annotations)
    set(KaRenderingOption.Annotations) { annotated ->
        previousValue(annotated).filter { annotation ->
            val fqName = annotation.classId?.asSingleFqName()
            fqName == null || fqName !in NULLABILITY_ANNOTATIONS
        }
    }
}

/**
 * Approximates types to the form which is denotable in the source position given by [position]: a covariant position is approximated to a
 * denotable supertype, a contravariant one to a denotable subtype, and an invariant position is left as is.
 */
@KaExperimentalApi
fun KaRendererBuilder.withTypeApproximation(position: Variance) {
    set(
        KaRenderingOption.TypeApproximation,
        when (position) {
            Variance.INVARIANT -> KaTypeApproximation.NONE
            Variance.IN_VARIANCE -> KaTypeApproximation.TO_DENOTABLE_SUBTYPE
            Variance.OUT_VARIANCE -> KaTypeApproximation.TO_DENOTABLE_SUPERTYPE
        },
    )
}

/**
 * Renders no annotations at all.
 *
 * Every annotation list routes through [KaPiece.Annotations], so this covers annotations on types, declarations, and parameters.
 */
@KaExperimentalApi
fun KaRendererBuilder.withoutAnnotations() {
    pushEmpty(KaPiece.Annotations)
}

/**
 * Renders a value parameter by the name written in Java source code, rather than the synthesized `p0`, `p1`, ... name the symbol carries.
 *
 * Java parameter names are absent from the bytecode unless the class was compiled with `-parameters`, in which case the symbol name is
 * synthesized. The written name is recovered from the `ClsParameterImpl` the symbol maps to, which reads it from the attached sources.
 *
 * A fake override breaks that recovery: `FirFakeOverrideGenerator.buildCopyForValueParameter` copies the parameter into a plain
 * `FirValueParameter`, which keeps the synthesized name of the original but is no longer a Java declaration and so no longer maps to a
 * `ClsParameterImpl`. The name is therefore looked up on [fakeOverrideOriginal], which is still the Java parameter. This covers substitution
 * and intersection overrides as well as delegated members.
 *
 * Kotlin parameters are unaffected, because their symbol already carries the written name.
 *
 * @see KaValueParameterSymbol.realName
 * @see KaValueParameterSymbol.hasSynthesizedName
 */
@KaExperimentalApi
private object JavaSourceParameterNameRenderer : KaPieceRenderer<KaNamedSymbol>(KaPiece.SymbolName) {
    context(session: KaSession, context: KaRenderingContext, output: KaRenderingOutput)
    override fun render(value: KaNamedSymbol, next: () -> Unit): Boolean {
        if (value !is KaValueParameterSymbol) return false

        // A parameter whose name is synthesized in both the bytecode and the attached sources has no written name to render, so the
        // synthesized one is left to the renderer below.
        val name = (value.fakeOverrideOriginal as? KaValueParameterSymbol ?: value).realName ?: return false
        output.append(name.render(), setOf(KaTextAttribute.Identifier, KaTextAttribute.Symbol(value)))
        return true
    }
}

/**
 * Renders value parameters by the names written in Java source code instead of the synthesized `p0`, `p1`, ... names the bytecode carries.
 */
@KaExperimentalApi
fun KaRendererBuilder.withJavaSourceParameterNames() {
    push(JavaSourceParameterNameRenderer)
}

/**
 * Renders a value parameter of a primary constructor as a plain parameter, without the `val`/`var` and the modifiers of the property it
 * declares, e.g. `n: Int` rather than `val n: Int`.
 *
 * Use this for text which describes a declaration rather than generating it, where naming the parameter is enough and the property it
 * declares is beside the point.
 */
@KaExperimentalApi
fun KaRendererBuilder.withoutPrimaryConstructorPropertyDeclarations() {
    push(KaPiece.ValueParameter) { parameter ->
        render(parameter, KaPiece.ValueParameterAnnotations)
        render(parameter, KaPiece.SymbolModifiers)
        render(parameter, KaPiece.SymbolName)
        render(parameter, KaPiece.ValueParameterType)
        render(parameter, KaPiece.ValueParameterDefaultValue)
    }
}

/**
 * Renders a reflection function type with function type syntax, e.g. `(Int) -> String` instead of `KFunction1<Int, String>`.
 *
 * A reflection function type is the only kind of function type which the built-in renderer sends to [KaPiece.ClassType], so a function type
 * arriving there is exactly the one to redirect.
 */
@KaExperimentalApi
fun KaRendererBuilder.withReflectionTypesAsFunctionTypes() {
    push(ReflectionFunctionTypeRenderer)
}

@KaExperimentalApi
private object ReflectionFunctionTypeRenderer : KaPieceRenderer<KaClassType>(KaPiece.ClassType) {
    context(session: KaSession, context: KaRenderingContext, output: KaRenderingOutput)
    override fun render(value: KaClassType, next: () -> Unit): Boolean {
        if (value !is KaFunctionType) return false

        render(value, KaPiece.FunctionType)
        return true
    }
}

/**
 * Renders an enum entry used as an annotation argument by its fully qualified name, e.g. `kotlin.DeprecationLevel.HIDDEN`.
 *
 * The built-in renderer always writes such an entry with the simple name of its enum class, regardless of
 * [KaRenderingOption.ClassTypeQualification].
 *
 * The name is rendered segment by segment — the package as [KaPiece.PackageName] and each class of the nesting chain, as well as the entry
 * itself, as [KaPiece.SymbolName] — so every segment is linked to the symbol it names. An entry whose enum class cannot be resolved is left
 * to the renderer beneath.
 */
@KaExperimentalApi
fun KaRendererBuilder.withQualifiedAnnotationEnumEntries() {
    push(QualifiedAnnotationEnumEntryRenderer)
}

@KaExperimentalApi
private object QualifiedAnnotationEnumEntryRenderer : KaPieceRenderer<KaAnnotationValue>(KaPiece.AnnotationValue) {
    context(session: KaSession, context: KaRenderingContext, output: KaRenderingOutput)
    override fun render(value: KaAnnotationValue, next: () -> Unit): Boolean {
        if (value !is KaAnnotationValue.EnumEntryValue) return false

        val callableId = value.callableId ?: return false
        val classId = callableId.classId ?: return false
        val enumClass = findClass(classId) ?: return false

        val packageName = callableId.packageName
        if (!packageName.isRoot && packageName != CallableId.PACKAGE_FQ_NAME_FOR_LOCAL) {
            render(packageName, KaPiece.PackageName)
            output.append(".", KaTextAttribute.Punctuation)
        }

        for (classSymbol in enumClass.withContainingClasses()) {
            if (classSymbol is KaNamedClassSymbol) {
                render(classSymbol, KaPiece.SymbolName)
                output.append(".", KaTextAttribute.Punctuation)
            }
        }

        val entry = enumClass.staticDeclaredMemberScope.callables(callableId.callableName).firstOrNull()
        if (entry is KaNamedSymbol) {
            render(entry, KaPiece.SymbolName)
        } else {
            output.append(callableId.callableName.render(), KaTextAttribute.Identifier)
        }
        return true
    }

    /** The class and the classes it is nested in, outermost first. */
    context(_: KaSession)
    private fun KaClassSymbol.withContainingClasses(): List<KaClassSymbol> {
        val classes = mutableListOf<KaClassSymbol>()
        var classSymbol: KaClassSymbol? = this
        while (classSymbol != null) {
            classes.add(classSymbol)
            classSymbol = classSymbol.containingDeclaration as? KaClassSymbol
        }
        return classes.asReversed()
    }
}

/**
 * Renders only the type of a value parameter, without its name, modifiers, or default value.
 *
 * The type is rendered as a plain [KaPiece.Type] rather than as [KaPiece.ValueParameterType], because the latter owns the `": "` which
 * separates it from the name being dropped here.
 */
@KaExperimentalApi
fun KaRendererBuilder.withValueParameterTypesOnly() {
    push(KaPiece.ValueParameter) { render(it.returnType, KaPiece.Type) }
}

/**
 * Renders a value parameter as its type, keeping a `vararg` prefix.
 *
 * See [withValueParameterTypesOnly] for why the type is rendered as a plain [KaPiece.Type].
 *
 * `vararg` is written directly instead of delegating to [KaPiece.SymbolModifiers] on purpose: it has to survive the [withoutModifiers] which
 * every caller of this helper also applies. Keeping it matters because these renderers produce signatures used to tell overloads apart, where
 * `foo(Int)` and `foo(vararg Int)` must not collapse into the same text. Delegating would additionally render `crossinline` and `noinline`,
 * which are irrelevant there.
 */
@KaExperimentalApi
fun KaRendererBuilder.withValueParameterTypesAndVararg() {
    push(KaPiece.ValueParameter) { parameter ->
        if (parameter.isVararg) {
            output.append(KtTokens.VARARG_KEYWORD.value, KaTextAttribute.Keyword)
            output.space()
        }
        render(parameter.returnType, KaPiece.Type)
    }
}

/**
 * The visibility modifier which [symbol] carries implicitly, i.e. the one the built-in modifier list omits because it is not written in
 * source code. `null` for a symbol which cannot carry a visibility at all.
 */
context(_: KaSession)
private fun implicitVisibilityModifier(symbol: KaDeclarationSymbol): KtModifierKeywordToken? = when (symbol) {
    is KaClassInitializerSymbol, is KaTypeParameterSymbol, is KaScriptSymbol, is KaParameterSymbol,
    is KaAnonymousFunctionSymbol, is KaAnonymousObjectSymbol,
        -> null

    else -> when (symbol.visibility) {
        KaSymbolVisibility.PUBLIC, KaSymbolVisibility.PACKAGE_PRIVATE -> KtTokens.PUBLIC_KEYWORD
        KaSymbolVisibility.PACKAGE_PROTECTED -> KtTokens.PROTECTED_KEYWORD
        // `private`, `protected` and `internal` are already in the default list; a local declaration renders no visibility.
        else -> null
    }
}

/**
 * Renders the visibility of every declaration, including the implicit `public`.
 */
@KaExperimentalApi
fun KaRendererBuilder.withImplicitVisibility() {
    set(KaRenderingOption.Modifiers) { symbol, modifiers ->
        val implicit = implicitVisibilityModifier(symbol)
        if (implicit == null || modifiers.any { it in KtTokens.VISIBILITY_MODIFIERS }) {
            modifiers
        } else {
            sortModifiers(modifiers + implicit)
        }
    }
}

/**
 * Renders a type argument without its `in`/`out` variance.
 *
 * Callers which generate source use this because a variance in a type argument position is a syntax error.
 */
@KaExperimentalApi
fun KaRendererBuilder.withoutTypeProjectionVariance() {
    push(KaPiece.TypeProjection) { projection ->
        when (projection) {
            is KaStarTypeProjection -> output.append("*", KaTextAttribute.Punctuation)
            is KaTypeArgumentWithVariance -> render(projection.type, KaPiece.Type)
        }
    }
}

/**
 * Renders a named function as `Receiver.name(ParameterType, …): ReturnType`, without keywords, modifiers, annotations, type parameters or
 * `where` clause.
 */
@KaExperimentalApi
fun KaRendererBuilder.withCompactFunctionSignature() {
    push(KaPiece.NamedFunction) { function ->
        function.receiverParameter?.let { render(it, KaPiece.ReceiverParameter) }
        render(function, KaPiece.SymbolName)
        render(function, KaPiece.ValueParameterList)
        render(function, KaPiece.NamedFunctionReturnType)
    }
}

/**
 * Renders a constructor as `ClassName(ParameterType, …)`, without the `constructor` keyword.
 */
@KaExperimentalApi
fun KaRendererBuilder.withCompactConstructorSignature() {
    push(KaPiece.Constructor) { constructor ->
        renderSymbolName(constructor.containingDeclaration)
        render(constructor, KaPiece.ValueParameterList)
    }
}

/**
 * Renders a class as its name and type parameters only, without the class keyword, supertypes, primary constructor or body.
 *
 * A dedicated renderer is needed rather than suppressing the pieces one by one, because the built-in [KaPiece.NamedClass] renderer emits the
 * space between the class keyword and the name itself.
 */
@KaExperimentalApi
fun KaRendererBuilder.withCompactClassSignature() {
    push(KaPiece.NamedClass) { classSymbol ->
        render(classSymbol, KaPiece.SymbolName)
        if (classSymbol.typeParameters.isNotEmpty()) {
            render(classSymbol, KaPiece.TypeParameterList)
        }
    }
}

/**
 * Renders the return type of a named function even when it is an implicit `Unit`.
 */
@KaExperimentalApi
fun KaRendererBuilder.withAlwaysRenderedReturnType() {
    push(KaPiece.NamedFunctionReturnType) { render(it, KaPiece.FunctionReturnType) }
}

/**
 * Renders the default value of a value parameter as `...`, even when it is a compile-time constant.
 */
@KaExperimentalApi
fun KaRendererBuilder.withEllipsisDefaultValues() {
    push(KaPiece.ValueParameterDefaultValueExpression) { output.append("...", KaTextAttribute.Punctuation) }
}

/**
 * Renders no modifiers, while keeping the declaration keywords (`fun`, `val`, `class`, …).
 */
@KaExperimentalApi
fun KaRendererBuilder.withoutModifiers() {
    set(KaRenderingOption.Modifiers) { _, _ -> emptyList() }
}

/**
 * Renders only the modifiers contained in [modifiers], dropping every other one.
 *
 * Unlike [withAllowedKeywords] this affects modifiers only, so keywords which are not modifiers (`fun`, `val`, the `out` of
 * `List<out String>`, the `suspend` of a suspend function type) are still rendered.
 */
@KaExperimentalApi
fun KaRendererBuilder.withOnlyModifiers(modifiers: TokenSet) {
    set(KaRenderingOption.Modifiers) { _, defaultModifiers -> defaultModifiers.filter { it in modifiers } }
}

/**
 * Renders every modifier except those contained in [modifiers].
 *
 * See [withOnlyModifiers] for how this differs from [withAllowedKeywords].
 */
@KaExperimentalApi
fun KaRendererBuilder.withoutModifiers(modifiers: TokenSet) {
    set(KaRenderingOption.Modifiers) { _, defaultModifiers -> defaultModifiers.filterNot { it in modifiers } }
}

/**
 * Renders only the given [keywords], suppressing every other declaration and type keyword.
 */
@KaExperimentalApi
fun KaRendererBuilder.withAllowedKeywords(vararg keywords: KtKeywordToken) {
    withAllowedKeywords(TokenSet.create(*keywords))
}

/**
 * Renders only the keywords contained in [keywords], suppressing every other declaration and type keyword.
 */
@KaExperimentalApi
fun KaRendererBuilder.withAllowedKeywords(keywords: TokenSet) {
    set(KaRenderingOption.AllowedKeywords) { token -> token in keywords }
}

/**
 * Renders no keywords at all, neither declaration keywords nor type keywords. Use [withoutDeclarationKeywords] to keep the latter.
 */
@KaExperimentalApi
fun KaRendererBuilder.withoutKeywords() {
    withAllowedKeywords(TokenSet.EMPTY)
}

/**
 * The keywords which introduce a declaration, as opposed to the keywords which are modifiers or part of a type.
 *
 * `context` is deliberately absent: dropping it would turn a context parameter list into a bare parameter list.
 */
private val DECLARATION_KEYWORDS: TokenSet = TokenSet.create(
    KtTokens.FUN_KEYWORD,
    KtTokens.VAL_KEYWORD,
    KtTokens.VAR_KEYWORD,
    KtTokens.CLASS_KEYWORD,
    KtTokens.INTERFACE_KEYWORD,
    KtTokens.OBJECT_KEYWORD,
    KtTokens.CONSTRUCTOR_KEYWORD,
    KtTokens.TYPE_ALIAS_KEYWORD,
    KtTokens.PACKAGE_KEYWORD,
    KtTokens.WHERE_KEYWORD,
    KtTokens.GET_KEYWORD,
    KtTokens.SET_KEYWORD,
    KtTokens.FIELD_KEYWORD,
)

/**
 * Suppresses the keywords which introduce a declaration (`fun`, `val`, `class`, `constructor`, …), while keeping the keywords which are part
 * of a type (the `out` of `List<out String>`, the `suspend` of a suspend function type, `dynamic`).
 *
 * Use [withoutModifiers] to drop modifiers as well; [withoutKeywords] drops type keywords too.
 */
@KaExperimentalApi
fun KaRendererBuilder.withoutDeclarationKeywords(except: TokenSet = TokenSet.EMPTY) {
    set(KaRenderingOption.AllowedKeywords) { token -> token !in DECLARATION_KEYWORDS || token in except }
}

/**
 * Renders type aliases expanded rather than by their abbreviation, e.g. `List<String>` instead of `MyAlias`.
 */
@KaExperimentalApi
fun KaRendererBuilder.withExpandedTypeAliases() {
    set(KaRenderingOption.ClassTypeRenderingMode, KaClassTypeRenderingMode.EXPANSION)
}

/**
 * Renders a type alias by its abbreviation, followed by its expansion in a comment, e.g. `MyAlias /* = List<String> */`.
 */
@KaExperimentalApi
fun KaRendererBuilder.withExpandedTypeAliasComment() {
    set(KaRenderingOption.ClassTypeRenderingMode, KaClassTypeRenderingMode.ABBREVIATION_WITH_EXPANSION_COMMENT)
}

/**
 * Renders an error return type as the type reference text of the original declaration, and falls through to the renderer beneath for any
 * other return type.
 *
 * Both return-type pieces own their leading `": "`, so the replacement emits it as well.
 */
@KaExperimentalApi
private class VerbatimErrorReturnTypeRenderer<T : KaCallableSymbol>(piece: KaPiece<T>) : KaPieceRenderer<T>(piece) {
    context(session: KaSession, context: KaRenderingContext, output: KaRenderingOutput)
    override fun render(value: T, next: () -> Unit): Boolean {
        if (value.returnType !is KaErrorType) return false

        val typeText = (value.fakeOverrideOriginal.psi as? KtCallableDeclaration)?.typeReference?.text ?: return false

        output.append(": ", KaTextAttribute.Punctuation)
        output.append(typeText, KaTextAttribute.Identifier)
        return true
    }
}

/**
 * Renders the return type of a callable as the type reference text of its original declaration, when the return type is an error type.
 *
 * A declaration read from malformed or unresolvable bytecode has an error return type, which would otherwise be rendered as `ERROR`. A
 * callable whose return type is fine, or which has no PSI text to take the type from, is left to the renderer beneath.
 *
 * Push this after [withAlwaysRenderedReturnType]: the renderer pushed last for a piece is the one consulted first, and
 * [withAlwaysRenderedReturnType] always consumes [KaPiece.NamedFunctionReturnType].
 */
@KaExperimentalApi
fun KaRendererBuilder.withPsiTextForErrorReturnType() {
    push(VerbatimErrorReturnTypeRenderer(KaPiece.NamedFunctionReturnType))
    push(VerbatimErrorReturnTypeRenderer(KaPiece.PropertyReturnType))
}

@KaExperimentalApi
@OptIn(ExperimentalContracts::class)
context(session: KaSession)
fun KaRenderer.renderToStringIfPresent(type: KaType?): String? {
    contract {
        returnsNotNull() implies (type != null)
    }

    if (type == null) {
        return null
    }

    return renderToString(type)
}

@KaExperimentalApi
context(session: KaSession, context: KaRenderingContext, output: KaRenderingOutput)
fun renderSymbolName(symbol: KaSymbol?, nameIfAbsent: Name = SpecialNames.NO_NAME_PROVIDED) {
    if (symbol is KaNamedSymbol) {
        render(symbol, KaPiece.SymbolName)
    } else {
        output.append(nameIfAbsent.render(), KaTextAttribute.Identifier)
    }
}