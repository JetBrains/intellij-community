// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.completion.impl.k2.contributors

import com.intellij.codeInsight.completion.CompletionType
import com.intellij.lang.jvm.JvmModifier
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.analysis.api.KaExperimentalApi
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.components.KaScopeKind
import org.jetbrains.kotlin.analysis.api.components.allSupertypes
import org.jetbrains.kotlin.analysis.api.components.buildClassType
import org.jetbrains.kotlin.analysis.api.components.buildSubstitutor
import org.jetbrains.kotlin.analysis.api.components.compositeScope
import org.jetbrains.kotlin.analysis.api.components.defaultType
import org.jetbrains.kotlin.analysis.api.components.isAnyType
import org.jetbrains.kotlin.analysis.api.components.memberScope
import org.jetbrains.kotlin.analysis.api.components.namedClassSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaClassKind
import org.jetbrains.kotlin.analysis.api.symbols.KaClassSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaNamedClassSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaTypeParameterSymbol
import org.jetbrains.kotlin.analysis.api.symbols.symbol
import org.jetbrains.kotlin.analysis.api.symbols.typeParameters
import org.jetbrains.kotlin.analysis.api.types.KaClassType
import org.jetbrains.kotlin.analysis.api.types.KaTypeParameterType
import org.jetbrains.kotlin.analysis.api.types.KaTypeProjection
import org.jetbrains.kotlin.analysis.api.types.symbol
import org.jetbrains.kotlin.idea.base.analysis.api.utils.isPossiblySubTypeOf
import org.jetbrains.kotlin.idea.codeinsight.utils.isOpen
import org.jetbrains.kotlin.idea.completion.impl.k2.K2CompletionContributor
import org.jetbrains.kotlin.idea.completion.impl.k2.K2CompletionSectionContext
import org.jetbrains.kotlin.idea.completion.impl.k2.K2CompletionSetupScope
import org.jetbrains.kotlin.idea.completion.impl.k2.K2ContributorSectionPriority
import org.jetbrains.kotlin.idea.completion.impl.k2.contributors.K2TypeInstantiationContributor.InheritanceSubstitutionResult.SubstitutionNotPossible
import org.jetbrains.kotlin.idea.completion.impl.k2.contributors.K2TypeInstantiationContributor.InheritanceSubstitutionResult.SuccessfulSubstitution
import org.jetbrains.kotlin.idea.completion.impl.k2.contributors.K2TypeInstantiationContributor.InheritanceSubstitutionResult.UnresolvedParameter
import org.jetbrains.kotlin.idea.completion.impl.k2.contributors.helpers.getAliasNameIfExists
import org.jetbrains.kotlin.idea.completion.impl.k2.lookups.ImportStrategy
import org.jetbrains.kotlin.idea.completion.impl.k2.lookups.factories.KotlinFirLookupElementFactory
import org.jetbrains.kotlin.idea.completion.impl.k2.lookups.factories.KotlinFirLookupElementFactory.createAnonymousObjectLookupElement
import org.jetbrains.kotlin.idea.completion.impl.k2.weighers.ExpectedTypeWeigher
import org.jetbrains.kotlin.idea.completion.impl.k2.weighers.ExpectedTypeWeigher.matchesExpectedType
import org.jetbrains.kotlin.idea.searching.inheritors.findAllInheritors
import org.jetbrains.kotlin.idea.util.positionContext.KotlinNameReferencePositionContext
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.name.StandardClassIds
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.KtObjectDeclaration
import org.jetbrains.kotlin.psi.psiUtil.isAbstract

/**
 * This contributor is responsible for completing type instantiation items for Kotlin classes and objects.
 * Type instantiation items are:
 *   - Constructor invocations
 *   - Usages of objects matching the expected type (even though it technically is not a type instantiation)
 *   - Creating anonymous subclasses
 * It is split into two parts:
 *   1. Contributing type instantiation items that match exactly the expected type
 *   2. Completing type instantiation items for matching types that are proper subtypes of the expected type.
 * Note that 2. is only done in smart completion.
 *
 * This contributor overlaps in parts with the [K2ClassifierCompletionContributor], so it should be ensured
 * that no duplicate results are emitted because of it.
 */
internal class K2TypeInstantiationContributor : K2CompletionContributor<KotlinNameReferencePositionContext>(
    KotlinNameReferencePositionContext::class
) {

    override fun K2CompletionSetupScope<KotlinNameReferencePositionContext>.isAppropriatePosition(): Boolean {
        // Cannot have type instantiation items when we have a receiver.
        return position.explicitReceiver == null
    }

    override fun K2CompletionSetupScope<KotlinNameReferencePositionContext>.registerCompletions() {
        completion("Exact Expected Type", priority = K2ContributorSectionPriority.HEURISTIC) {
            completeExactExpectedTypeMatch()
        }

        // We only add subtypes for smart completion
        if (completionContext.parameters.completionType != CompletionType.SMART) return
        completion("Subtypes", priority = K2ContributorSectionPriority.FROM_INDEX) {
            completeSubtypes()
        }
    }

    context(_: KaSession, context: K2CompletionSectionContext<KotlinNameReferencePositionContext>)
    private fun addAnonymousObjectLookupElement(
        symbol: KaClassSymbol,
        typeArguments: List<KaTypeProjection>?,
        importingStrategy: ImportStrategy,
        aliasName: Name?
    ) {
        val element = createAnonymousObjectLookupElement(symbol, typeArguments, importingStrategy, aliasName)
        element.matchesExpectedType = ExpectedTypeWeigher.MatchesExpectedType.MATCHES
        addElement(element)
    }

    context(_: KaSession, context: K2CompletionSectionContext<KotlinNameReferencePositionContext>)
    private fun addObjectLookupElement(
        symbol: KaClassSymbol,
        importingStrategy: ImportStrategy,
        aliasName: Name?
    ) {
        KotlinFirLookupElementFactory.createClassifierLookupElement(
            symbol = symbol,
            importingStrategy = importingStrategy,
            aliasName = aliasName,
        )?.let { element ->
            element.matchesExpectedType = ExpectedTypeWeigher.MatchesExpectedType.MATCHES
            addElement(element)
        }
    }

    context(_: KaSession, context: K2CompletionSectionContext<KotlinNameReferencePositionContext>)
    private fun addConstructorCallLookupElements(
        symbol: KaNamedClassSymbol,
        inputTypeArgumentsAreRequired: Boolean,
        importStrategy: ImportStrategy,
        aliasName: Name?
    ) {
        val constructorSymbols = symbol.memberScope.constructors
            .filter { context.visibilityChecker.isVisible(it, context.positionContext) }
            .toList()

        if (symbol.isInner) {
            // TODO: we do not return inner classes from this contributor, but they are returned by other contributors
            return
        }

        KotlinFirLookupElementFactory.createConstructorCallLookupElement(
            containingSymbol = symbol,
            visibleConstructorSymbols = constructorSymbols,
            importingStrategy = importStrategy,
            aliasName = aliasName,
            inputTypeArgumentsAreRequired = inputTypeArgumentsAreRequired,
        )?.let { element ->
            element.matchesExpectedType = ExpectedTypeWeigher.MatchesExpectedType.MATCHES
            addElement(element)
        }
    }

    /**
     * Returns if the given [PsiElement] can potentially be instantiated at the position of the completion context, taking
     * into account visibility rules and whether the element allows instantiation at all.
     */
    context(context: K2CompletionSectionContext<KotlinNameReferencePositionContext>)
    private fun PsiElement.canBeInstantiated(): Boolean = when (this) {
        is KtClass -> context.visibilityChecker.canBeVisible(this) && !isAbstract()
        is PsiClass -> context.visibilityChecker.canBeVisible(this) && !hasModifier(JvmModifier.ABSTRACT) && !isInterface
        else -> false
    }

    /**
     * Returns if the given [PsiElement] can be inherted at the position of the completion context, taking
     * into account visibility rules and whether the element allows inheritance at all.
     */
    context(context: K2CompletionSectionContext<KotlinNameReferencePositionContext>)
    private fun PsiElement.canBeInherited(): Boolean = when (this) {
        is KtClass -> context.visibilityChecker.canBeVisible(this) &&
                (isOpen() || isAbstract()) && !isSealed()

        is PsiClass ->
            // For Java classes we only show results for abstract classes or interfaces to not
            // pollute the results too much because Java classes are open by default
            context.visibilityChecker.canBeVisible(this) && (isInterface || hasModifier(JvmModifier.ABSTRACT))

        else -> false
    }

    /**
     * Completes type instantiation items for the expected type.
     * This does not complete subtypes of the expected type, which is done in [completeSubtypes].
     */
    context(_: KaSession, context: K2CompletionSectionContext<KotlinNameReferencePositionContext>)
    private fun completeExactExpectedTypeMatch() {
        val expectedType = context.weighingContext.expectedType ?: return
        val expectedSymbol = expectedType.symbol

        if (expectedSymbol !is KaNamedClassSymbol || expectedType !is KaClassType) return

        val aliasName = context.parameters.completionFile.getAliasNameIfExists(expectedSymbol)
        val importingStrategy = if (aliasName == null) {
            context.importStrategyDetector.detectImportStrategyForClassifierSymbol(expectedSymbol)
        } else ImportStrategy.DoNothing

        if (expectedSymbol.psi?.canBeInstantiated() == true) {
            addConstructorCallLookupElements(
                symbol = expectedSymbol,
                inputTypeArgumentsAreRequired = false,
                importStrategy = importingStrategy,
                aliasName = aliasName
            )
        }

        if (expectedSymbol.psi?.canBeInherited() == true) {
            val typeArguments = expectedType.typeArguments

            // If the expected type contains some type parameters, then we have to let the user fill them in,
            // unless the type parameters are also available in the same scope.
            val potentiallyUnresolvedTypeParameters = typeArguments.mapNotNull { it.type }.filterIsInstance<KaTypeParameterType>()
            val hasUnresolvedArguments = if (potentiallyUnresolvedTypeParameters.isNotEmpty()) {
                val typeParametersScope = context.weighingContext.scopeContext.compositeScope { it is KaScopeKind.TypeParameterScope }
                val availableTypeParameters = typeParametersScope.classifiers.filterIsInstance<KaTypeParameterSymbol>()
                potentiallyUnresolvedTypeParameters.any { it.symbol !in availableTypeParameters }
            } else false

            // If the expected type is _exactly_ the type we want to inherit, then the type arguments
            // required are exactly the ones of the expected type.
            addAnonymousObjectLookupElement(
                expectedSymbol, typeArguments.takeIf { !hasUnresolvedArguments },
                importingStrategy = importingStrategy,
                aliasName = aliasName
            )
        }
    }

    private sealed interface InheritanceSubstitutionResult {
        object SubstitutionNotPossible : InheritanceSubstitutionResult
        object UnresolvedParameter : InheritanceSubstitutionResult
        class SuccessfulSubstitution(val typeArguments: List<KaTypeProjection>) : InheritanceSubstitutionResult
    }

    /**
     * Given the type, returns a map of how the corresponding symbol's type arguments are mapped
     * to instantiate the symbol to the type.
     */
    @OptIn(KaExperimentalApi::class)
    context(_: KaSession)
    private fun KaClassType.getTypeParameterMapping(): Map<KaTypeParameterSymbol, KaTypeProjection> {
        return symbol.typeParameters.zip(typeArguments).toMap()
    }

    /**
     * Given an [expectedSuperType] with its corresponding [expectedSuperTypeParameterMapping], this function calculates
     * a mapping of the [inheritorSymbol]'s type parameters so that when applied will result in a type that will
     * be a subtype of the [expectedSuperType].
     * If it is not possible to instantiate the [inheritorSymbol] in such a way, then the function returns [SubstitutionNotPossible].
     * If there are unresolved type parameters or there are multiple ways to instantiate the symbol,
     * then the function returns [UnresolvedParameter].
     *
     * Note: this does not work in all cases and there are more complex cases that are not covered.
     * These cases will return [UnresolvedParameter] as a safe behavior which is sufficient for now.
     */
    @OptIn(KaExperimentalApi::class)
    context(_: KaSession)
    private fun substituteTypeArgumentsToMatchExpectedSupertype(
        inheritorSymbol: KaClassSymbol,
        expectedSuperTypeParameterMapping: Map<KaTypeParameterSymbol, KaTypeProjection>,
        expectedSuperType: KaClassType,
    ): InheritanceSubstitutionResult {
        val superType = inheritorSymbol.defaultType.allSupertypes.firstOrNull { it.symbol == expectedSuperType.symbol }
                as? KaClassType ?: return SubstitutionNotPossible
        val superTypeMapping = superType.getTypeParameterMapping()

        // Create a reverse mapping from super type
        val reverseSuperTypeMapping = buildList {
            for ((preImage, image) in superTypeMapping) {
                val imageType = image.type
                if (imageType !is KaTypeParameterType) continue
                add(imageType.symbol to preImage)
            }
        }.groupBy({ it.first }) { it.second }

        // Example:
        // superSymbolParameterMapping: Comparable<String> => in T -> String
        // reverseSuperTypeMapping: Foo<T> : Comparable<T> => T -> in T
        // Now compose the reverseSuperTypeMapping and the superSymbolParameterMapping
        val mappedTypeArgs = inheritorSymbol.typeParameters.map { typeParamSymbol ->
            // Note: there might be multiple results here, which is fine as long as all the results
            // map to a _single_ result in the end.
            val reverseTypes = reverseSuperTypeMapping[typeParamSymbol] ?: return UnresolvedParameter
            val mappedTypes = reverseTypes.mapTo(mutableSetOf()) { expectedSuperTypeParameterMapping[it] }
            // Only take the result if it maps to a single output type in the end
            val singleMappedType = mappedTypes.distinctBy { it?.type }.singleOrNull()
            singleMappedType ?: return UnresolvedParameter
        }

        // Based on the type arguments, we build a substitutor to map the `inheritorSymbol` to the concrete type that will be used.
        val substitutor = buildSubstitutor {
            inheritorSymbol.typeParameters.zip(mappedTypeArgs).forEach { (typeParam, typeArg) ->
                val type = typeArg.type ?: buildClassType(StandardClassIds.Any) {
                    isMarkedNullable = true
                }
                substitution(typeParam, type)
            }
        }

        // We apply the substitution. It's possible that the resulting type is not a subtype of the expected type,
        // in which case the element does not match and we return [SubstitutionNotPossible].
        val substituted = substitutor.substitute(inheritorSymbol.defaultType)
        // The analysis API's `isSubtypeOf` method does not work properly if free type parameters are involved.
        // To still show results in that case, we use `isPossiblySubTypeOf`, which can lead to elements
        // being allowed to be shown even if they do not actually work in the cotext.
        if (!substituted.isPossiblySubTypeOf(expectedSuperType)) {
            return SubstitutionNotPossible
        }

        // Return the types that will be used to instantiate the type parameters in symbol.
        return SuccessfulSubstitution(mappedTypeArgs)
    }

    /**
     * Completes type instantiation items for matching types that are proper subtypes of the expected type.
     */
    @OptIn(KaExperimentalApi::class)
    context(_: KaSession, context: K2CompletionSectionContext<KotlinNameReferencePositionContext>)
    private fun completeSubtypes() {
        val expectedType = context.weighingContext.expectedType ?: return
        if (expectedType.isAnyType) {
            // There would be too many results as every class would match
            return
        }
        if (expectedType !is KaClassType) return

        val expectedTypeParamMap = expectedType.getTypeParameterMapping()

        val inheritingClasses = expectedType.symbol.psi?.findAllInheritors() ?: return
        for (inheritor in inheritingClasses) {
            val canBeInherited = inheritor.canBeInherited()
            val canBeInstantiated = inheritor.canBeInstantiated()
            val isObject = inheritor is KtObjectDeclaration
            if (!canBeInherited && !canBeInstantiated && !isObject) continue

            val inheritorSymbol = when (inheritor) {
                is KtClassOrObject -> inheritor.symbol as? KaNamedClassSymbol ?: continue
                is PsiClass -> inheritor.namedClassSymbol ?: continue
                else -> continue
            }
            val aliasName = context.parameters.completionFile.getAliasNameIfExists(inheritorSymbol)
            val importStrategy = if (aliasName == null) {
                context.importStrategyDetector.detectImportStrategyForClassifierSymbol(inheritorSymbol)
            } else ImportStrategy.DoNothing

            if (inheritorSymbol.classKind == KaClassKind.ENUM_CLASS) {
                // Enum is not allowed to be instantiated
                continue
            }

            val substitutionResult = substituteTypeArgumentsToMatchExpectedSupertype(
                inheritorSymbol = inheritorSymbol,
                expectedSuperTypeParameterMapping = expectedTypeParamMap,
                expectedSuperType = expectedType,
            )

            if (canBeInherited) {
                val typeArgs = when (substitutionResult) {
                    is SuccessfulSubstitution -> substitutionResult.typeArguments
                    SubstitutionNotPossible -> continue // do not show the result
                    UnresolvedParameter -> null // show the result, but let user complete type arguments
                }

                addAnonymousObjectLookupElement(
                    symbol = inheritorSymbol,
                    typeArguments = typeArgs,
                    importingStrategy = importStrategy,
                    aliasName = aliasName
                )
            }

            if (canBeInstantiated) {
                val typeArgsRequired = when (substitutionResult) {
                    is SuccessfulSubstitution -> substitutionResult.typeArguments.isNotEmpty()
                    SubstitutionNotPossible -> continue // do not show the result
                    UnresolvedParameter -> true
                }
                addConstructorCallLookupElements(
                    symbol = inheritorSymbol,
                    inputTypeArgumentsAreRequired = typeArgsRequired,
                    importStrategy = importStrategy,
                    aliasName = aliasName,
                )
            }

            if (isObject) {
                addObjectLookupElement(
                    symbol = inheritorSymbol,
                    importingStrategy = importStrategy,
                    aliasName = aliasName,
                )
            }
        }
    }
}