// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.completion.impl.k2.lookups.factories

import com.intellij.codeInsight.completion.InsertionContext
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.codeInsight.lookup.LookupElementBuilder
import kotlinx.serialization.Serializable
import org.jetbrains.kotlin.analysis.api.KaExperimentalApi
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.components.asSignature
import org.jetbrains.kotlin.analysis.api.components.defaultType
import org.jetbrains.kotlin.analysis.api.components.importableFqName
import org.jetbrains.kotlin.analysis.api.components.lowerBoundIfFlexible
import org.jetbrains.kotlin.analysis.api.components.render
import org.jetbrains.kotlin.analysis.api.components.upperBoundIfFlexible
import org.jetbrains.kotlin.analysis.api.renderer.types.impl.KaTypeRendererForSource.WITH_SHORT_NAMES
import org.jetbrains.kotlin.analysis.api.symbols.KaClassKind
import org.jetbrains.kotlin.analysis.api.symbols.KaClassLikeSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaConstructorSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaNamedFunctionSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaSamConstructorSymbol
import org.jetbrains.kotlin.analysis.api.symbols.nameOrAnonymous
import org.jetbrains.kotlin.analysis.api.types.KaStarTypeProjection
import org.jetbrains.kotlin.analysis.api.types.KaTypeArgumentWithVariance
import org.jetbrains.kotlin.analysis.api.types.KaTypeProjection
import org.jetbrains.kotlin.idea.KotlinIcons
import org.jetbrains.kotlin.idea.base.analysis.withRootPrefixIfNeeded
import org.jetbrains.kotlin.idea.base.serialization.names.KotlinFqNameSerializer
import org.jetbrains.kotlin.idea.base.serialization.names.KotlinNameSerializer
import org.jetbrains.kotlin.idea.completion.impl.k2.contributors.ChainedInsertHandler
import org.jetbrains.kotlin.idea.completion.impl.k2.handlers.AnonymousObjectInsertHandler
import org.jetbrains.kotlin.idea.completion.impl.k2.handlers.TrailingLambdaInsertionHandler
import org.jetbrains.kotlin.idea.completion.impl.k2.lookups.CallableInsertionOptions
import org.jetbrains.kotlin.idea.completion.impl.k2.lookups.CallableInsertionStrategy
import org.jetbrains.kotlin.idea.completion.impl.k2.lookups.CompletionShortNamesRenderer
import org.jetbrains.kotlin.idea.completion.impl.k2.lookups.ImportStrategy
import org.jetbrains.kotlin.idea.completion.impl.k2.lookups.KotlinLookupObject
import org.jetbrains.kotlin.idea.completion.impl.k2.lookups.QuotedNamesAwareInsertionHandler
import org.jetbrains.kotlin.idea.completion.impl.k2.lookups.TailTextProvider
import org.jetbrains.kotlin.idea.completion.impl.k2.lookups.addImportIfRequired
import org.jetbrains.kotlin.idea.completion.impl.k2.lookups.renderVerbose
import org.jetbrains.kotlin.idea.completion.impl.k2.lookups.withClassifierSymbolInfo
import org.jetbrains.kotlin.idea.completion.impl.k2.weighers.KindWeigher.isConstructorCall
import org.jetbrains.kotlin.idea.completion.impl.k2.weighers.TrailingLambdaWeigher.hasTrailingLambda
import org.jetbrains.kotlin.idea.util.realName
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.renderer.render
import org.jetbrains.kotlin.types.Variance

internal object ClassLookupElementFactory {

    context(_: KaSession)
    fun createLookup(
        symbol: KaClassLikeSymbol,
        importingStrategy: ImportStrategy,
        aliasName: Name? = null,
    ): LookupElementBuilder {
        val name = aliasName ?: symbol.nameOrAnonymous
        return LookupElementBuilder.create(ClassifierLookupObject(name, importingStrategy), name.asString())
            .withInsertHandler(ClassifierInsertionHandler)
            .withTailText(TailTextProvider.getTailText(symbol, useFqnAsTailText = aliasName != null), true)
            .let { withClassifierSymbolInfo(symbol, it) }
    }

    context(_: KaSession)
    fun createAnonymousObjectLookup(
        symbol: KaClassLikeSymbol,
        classKind: KaClassKind,
        typeArguments: List<KaTypeProjection>?,
        importingStrategy: ImportStrategy,
        aliasName: Name? = null,
    ): LookupElementBuilder {
        val name = aliasName ?: symbol.nameOrAnonymous
        val constructorParenthesis = if (classKind != KaClassKind.INTERFACE) "()" else ""
        val hasTypeArguments = typeArguments == null || typeArguments.isNotEmpty()

        @OptIn(KaExperimentalApi::class)
        val renderedFullTypeArgs = typeArguments?.takeIf { hasTypeArguments }?.joinToString(", ", "<", ">") {
            when (it) {
                is KaStarTypeProjection -> "Any?"
                is KaTypeArgumentWithVariance -> it.type.upperBoundIfFlexible().render(position = Variance.INVARIANT)
            }
        }

        @OptIn(KaExperimentalApi::class)
        val renderedShortTypeArgs = typeArguments?.takeIf { hasTypeArguments }?.joinToString(", ", "<", ">") {
            when (it) {
                is KaStarTypeProjection -> "Any?"
                is KaTypeArgumentWithVariance -> it.type.upperBoundIfFlexible()
                    .render(renderer = WITH_SHORT_NAMES, position = Variance.INVARIANT)
            }
        }

        val itemText = buildString {
            append("object : ")
            append(name.asString())
            if (renderedShortTypeArgs != null) {
                append(renderedShortTypeArgs)
            } else if (hasTypeArguments) {
                append("<...>")
            }
            append(constructorParenthesis)
            append("{...}")
        }

        val fqName = symbol.importableFqName ?: FqName.topLevel(name)
        val insertHandler = AnonymousObjectInsertHandler(
            constructorParenthesis = constructorParenthesis,
            renderedClassifier = symbol.importableFqName?.withRootPrefixIfNeeded()?.asString() ?: name.asString(),
            renderedTypeArgs = renderedFullTypeArgs,
            hasTypeArguments = hasTypeArguments,
        )

        return LookupElementBuilder.create(AnonymousObjectLookupObject(name, importingStrategy, fqName), "object")
            .withPresentableText(itemText)
            .withLookupString(name.asString())
            .withInsertHandler(insertHandler)
            .withTailText(TailTextProvider.getTailText(symbol, addTypeParameters = false, useFqnAsTailText = aliasName != null), true)
            .let { withClassifierSymbolInfo(symbol, it) }
    }

    @OptIn(KaExperimentalApi::class)
    context(_: KaSession)
    fun createConstructorLookup(
        containingSymbol: KaClassLikeSymbol,
        constructorSymbols: List<KaConstructorSymbol>,
        inputTypeArgumentsAreRequired: Boolean,
        importingStrategy: ImportStrategy,
        aliasName: Name? = null,
    ): LookupElementBuilder {
        val name = aliasName ?: containingSymbol.nameOrAnonymous
        val singleConstructor = constructorSymbols.singleOrNull()
        val valueParameters = singleConstructor?.valueParameters?.map { it.asSignature() }

        val options = CallableInsertionOptions(
            importingStrategy = importingStrategy,
            insertionStrategy = CallableInsertionStrategy.AsCall
        )

        val lookupObject = FunctionCallLookupObject(
            shortName = name,
            options = options,
            renderedDeclaration = valueParameters?.let { CompletionShortNamesRenderer.renderFunctionParameters(it) } ?: "(...)",
            hasReceiver = false,
            inputValueArgumentsAreRequired = constructorSymbols.size > 1 || valueParameters?.isNotEmpty() == true,
            inputTypeArgumentsAreRequired = inputTypeArgumentsAreRequired,
            isConstructorCall = true,
        )
        return LookupElementBuilder.create(lookupObject, name.asString())
            .withInsertHandler(FunctionInsertionHandler)
            .appendTailText(lookupObject.renderedDeclaration, true)
            .appendTailText(TailTextProvider.getTailText(containingSymbol, useFqnAsTailText = aliasName != null), true)
            .let {
                it.isConstructorCall = true
                withClassifierSymbolInfo(containingSymbol, it)
            }
    }

    @OptIn(KaExperimentalApi::class)
    context(s: KaSession)
    fun createSamObjectLookupElement(
        samInterfaceSymbol: KaClassLikeSymbol,
        samFunction: KaNamedFunctionSymbol,
        samConstructorSymbol: KaSamConstructorSymbol,
        importingStrategy: ImportStrategy,
        inputTypeArgumentsAreRequired: Boolean,
        aliasName: Name?,
    ): LookupElementBuilder {
        val name = samInterfaceSymbol.nameOrAnonymous

        val options = CallableInsertionOptions(
            importingStrategy = importingStrategy,
            insertionStrategy = CallableInsertionStrategy.AsCall
        )

        val valueParameters = samFunction.valueParameters

        val samConstructorParameters = samConstructorSymbol.valueParameters.map { it.asSignature() }

        val renderedNames = if (valueParameters.size <= 1) {
            " {...} "
        } else {
            samFunction.valueParameters.joinToString(prefix = " { ", postfix = " -> ... } ") {
                val renderedType = it.returnType
                    .lowerBoundIfFlexible()
                    .render(renderer = WITH_SHORT_NAMES, position = Variance.INVARIANT)
                it.realName?.asString() ?: renderedType
            }
        }

        val trailingLambdaInsertHandler = TrailingLambdaInsertionHandler.create(samFunction, skipBraces = true)

        val insertHandler = if (valueParameters.size > 1 && trailingLambdaInsertHandler != null) {
            ChainedInsertHandler(FunctionInsertionHandler, trailingLambdaInsertHandler)
        } else {
            FunctionInsertionHandler
        }

        val lookupObject = FunctionCallLookupObject(
            shortName = name,
            options = options,
            renderedDeclaration = CompletionShortNamesRenderer.renderFunctionParameters(samConstructorParameters),
            hasReceiver = false,
            inputTrailingLambdaIsRequired = true,
            inputTypeArgumentsAreRequired = inputTypeArgumentsAreRequired,
            isConstructorCall = true,
        )

        val element = LookupElementBuilder.create(lookupObject, name.asString())
            .withInsertHandler(insertHandler)
            .appendTailText(renderedNames, true)
            .appendTailText(lookupObject.renderedDeclaration, true)
            .appendTailText(
                TailTextProvider.getTailText(
                    samInterfaceSymbol,
                    useFqnAsTailText = aliasName != null,
                    addTypeParameters = false
                ), true
            )

        element.hasTrailingLambda = true
        element.isConstructorCall = true

        return withClassifierSymbolInfo(samInterfaceSymbol, element)
            .withTypeText(samInterfaceSymbol.defaultType.renderVerbose())
            .withIcon(KotlinIcons.FUNCTION)
    }
}


@Serializable
internal data class ClassifierLookupObject(
    @Serializable(with = KotlinNameSerializer::class) override val shortName: Name,
    val importingStrategy: ImportStrategy
) : KotlinLookupObject

@Serializable
internal data class AnonymousObjectLookupObject(
    @Serializable(with = KotlinNameSerializer::class) override val shortName: Name,
    val importingStrategy: ImportStrategy,
    @Serializable(with = KotlinFqNameSerializer::class)
    val fqName: FqName,
) : KotlinLookupObject

/**
 * The simplest implementation of the insertion handler for a classifiers.
 */
@Serializable
internal object ClassifierInsertionHandler : QuotedNamesAwareInsertionHandler() {

    override fun handleInsert(context: InsertionContext, item: LookupElement) {
        val targetFile = context.file as? KtFile ?: return
        val lookupObject = item.`object` as ClassifierLookupObject
        val importingStrategy = lookupObject.importingStrategy

        super.handleInsert(context, item)

        if (importingStrategy is ImportStrategy.InsertFqNameAndShorten) {
            val shortenCommand = item.shortenCommand
                ?.takeUnless { it.isEmpty }

            val fqName = importingStrategy.fqName
                .withRootPrefixIfNeeded()

            context.insertAndShortenReferencesInStringUsingTemporarySuffix(
                string = fqName.render(),
                shortenCommand = shortenCommand,
            )
        } else if (importingStrategy is ImportStrategy.AddImport) {
            addImportIfRequired(context, importingStrategy.nameToImport)
        }
    }
}
