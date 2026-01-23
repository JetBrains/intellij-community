// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.completion.impl.k2.contributors

import com.intellij.codeInsight.completion.CompletionType
import com.intellij.lang.jvm.JvmModifier
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.analysis.api.KaExperimentalApi
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.components.*
import org.jetbrains.kotlin.analysis.api.symbols.*
import org.jetbrains.kotlin.analysis.api.types.KaClassType
import org.jetbrains.kotlin.analysis.api.types.KaTypeParameterType
import org.jetbrains.kotlin.analysis.api.types.KaTypeProjection
import org.jetbrains.kotlin.analysis.api.types.symbol
import org.jetbrains.kotlin.idea.codeinsight.utils.isOpen
import org.jetbrains.kotlin.idea.completion.impl.k2.K2CompletionContributor
import org.jetbrains.kotlin.idea.completion.impl.k2.K2CompletionSectionContext
import org.jetbrains.kotlin.idea.completion.impl.k2.K2CompletionSetupScope
import org.jetbrains.kotlin.idea.completion.impl.k2.K2ContributorSectionPriority
import org.jetbrains.kotlin.idea.completion.impl.k2.contributors.K2TypeInstantiationContributor.InheritanceSubstitutionResult.*
import org.jetbrains.kotlin.idea.completion.lookups.factories.KotlinFirLookupElementFactory.createAnonymousObjectLookupElement
import org.jetbrains.kotlin.idea.completion.weighers.ExpectedTypeWeigher
import org.jetbrains.kotlin.idea.completion.weighers.ExpectedTypeWeigher.matchesExpectedType
import org.jetbrains.kotlin.idea.searching.inheritors.findAllInheritors
import org.jetbrains.kotlin.idea.util.positionContext.KotlinNameReferencePositionContext
import org.jetbrains.kotlin.name.StandardClassIds
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.psiUtil.isAbstract

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
    private fun addAnonymousObjectLookupElement(symbol: KaClassSymbol, typeArguments: List<KaTypeProjection>?) {
        val element = createAnonymousObjectLookupElement(symbol, typeArguments)
        element.matchesExpectedType = ExpectedTypeWeigher.MatchesExpectedType.MATCHES
        addElement(element)
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

        if (expectedSymbol !is KaClassSymbol || expectedType !is KaClassType) return
        if (expectedSymbol.psi?.canBeInherited() == false) return

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
        addAnonymousObjectLookupElement(expectedSymbol, typeArguments.takeIf { !hasUnresolvedArguments })
    }

    private sealed interface InheritanceSubstitutionResult {
        object SubstitutionNotPossible : InheritanceSubstitutionResult
        object UnresolvedParameter : InheritanceSubstitutionResult
        class SuccessfulSubstitution(val typeArguments: List<KaTypeProjection>?) : InheritanceSubstitutionResult
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
        if (!substituted.isSubtypeOf(expectedSuperType)) {
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
            if (!canBeInherited) continue

            val inheritorSymbol = when (inheritor) {
                is KtClass -> inheritor.symbol as? KaNamedClassSymbol ?: continue
                is PsiClass -> inheritor.namedClassSymbol ?: continue
                else -> continue
            }

            if (inheritorSymbol.classKind == KaClassKind.ENUM_CLASS) {
                // Enum is not allowed to be extended by anonymous objects
                continue
            }

            val substitutionResult = substituteTypeArgumentsToMatchExpectedSupertype(
                inheritorSymbol = inheritorSymbol,
                expectedSuperTypeParameterMapping = expectedTypeParamMap,
                expectedSuperType = expectedType,
            )

            val typeArgs = when (substitutionResult) {
                is SuccessfulSubstitution -> substitutionResult.typeArguments
                SubstitutionNotPossible -> continue // do not show the result
                UnresolvedParameter -> null // show the result, but let user complete type arguments
            }

            addAnonymousObjectLookupElement(inheritorSymbol, typeArgs)
        }
    }
}