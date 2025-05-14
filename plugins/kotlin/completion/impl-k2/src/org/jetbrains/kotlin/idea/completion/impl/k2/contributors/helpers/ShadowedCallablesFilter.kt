// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.completion.contributors.helpers

import com.intellij.openapi.util.Ref
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.components.KaScopeKind
import org.jetbrains.kotlin.analysis.api.components.KaTypeRelationChecker
import org.jetbrains.kotlin.analysis.api.signatures.KaCallableSignature
import org.jetbrains.kotlin.analysis.api.signatures.KaFunctionSignature
import org.jetbrains.kotlin.analysis.api.signatures.KaVariableSignature
import org.jetbrains.kotlin.analysis.api.symbols.*
import org.jetbrains.kotlin.analysis.api.types.KaFunctionType
import org.jetbrains.kotlin.analysis.api.types.KaType
import org.jetbrains.kotlin.idea.completion.impl.k2.ImportStrategyDetector
import org.jetbrains.kotlin.idea.completion.impl.k2.checkers.ApplicableExtension
import org.jetbrains.kotlin.idea.completion.lookups.CallableInsertionOptions
import org.jetbrains.kotlin.idea.completion.lookups.CallableInsertionStrategy
import org.jetbrains.kotlin.idea.completion.lookups.ImportStrategy
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name

internal class ShadowedCallablesFilter {

    data class FilterResult(
        val excludeFromCompletion: Boolean,
        val newImportStrategy: ImportStrategy? = null,
    )

    private val processed = HashSet<SimplifiedSignature>()

    // todo reconsider Ref
    private val processedSimplifiedSignatures = HashMap<SimplifiedSignature, Ref<KaScopeKind>>()

    /**
     *  Checks whether callable is shadowed and updates [CallableInsertionOptions] if the callable is already imported and its short name
     *  can be used without being shadowed.
     *
     *  When the fully qualified function name is inserted, [org.jetbrains.kotlin.idea.base.analysis.api.utils.shortenReferences] is invoked
     *  on the call with missing arguments and shortened version is resolved to a different symbol, which leads to shortening not being
     *  invoked. For example, `kotlin.text.String()` is not shortened by reference shortener, because shortened version `String()`
     *  is resolved to `kotlin.String()`. That's why we can't rely on reference shortener and need to use [ImportStrategy.DoNothing].
     */
    context(KaSession)
    fun excludeFromCompletion(
        callableSignature: KaCallableSignature<*>,
        options: CallableInsertionOptions,
        scopeKind: KaScopeKind?,
        importStrategyDetector: ImportStrategyDetector,
        requiresTypeArguments: (KaFunctionSymbol) -> Boolean,
    ): FilterResult {
        val (importStrategy, insertionStrategy) = options
        val fullSimplifiedSignature = when (callableSignature) {
            is KaVariableSignature<*> -> when (insertionStrategy) {
                is CallableInsertionStrategy.AsCall -> FunctionLikeSimplifiedSignature.create(callableSignature)
                else -> VariableLikeSimplifiedSignature.create(callableSignature)
            }

            is KaFunctionSignature<*> -> FunctionLikeSimplifiedSignature.create(callableSignature) { requiresTypeArguments(it) }
        } ?: return FilterResult(excludeFromCompletion = false)

        if (!processed.add(fullSimplifiedSignature)) return FilterResult(excludeFromCompletion = true)

        val simplifiedSignature = when (fullSimplifiedSignature) {
            is VariableLikeSimplifiedSignature -> fullSimplifiedSignature.copy(containerFqName = null)
            is FunctionLikeSimplifiedSignature -> fullSimplifiedSignature.copy(containerFqName = null)
        }

        fun isAlreadyImported() = with(importStrategyDetector) {
            val callableId = callableSignature.callableId
            callableId != null
                    && callableId.asSingleFqName().isAlreadyImported()
        }

        // if callable is already imported, try updating importing strategy
        if (importStrategy != ImportStrategy.DoNothing
            && (scopeKind != null || isAlreadyImported())
        ) {
            val newImportStrategy = ImportStrategy.DoNothing
            val excludeFromCompletion =
                processSignatureConsideringOptions(fullSimplifiedSignature, simplifiedSignature, newImportStrategy, scopeKind)
            if (!excludeFromCompletion) {
                return FilterResult(excludeFromCompletion = false, newImportStrategy)
            }
        }

        val excludeFromCompletion =
            processSignatureConsideringOptions(fullSimplifiedSignature, simplifiedSignature, importStrategy, scopeKind)
        return FilterResult(excludeFromCompletion)
    }

    context(KaSession)
    private fun processSignatureConsideringOptions(
        fullSimplifiedSignature: SimplifiedSignature,
        simplifiedSignature: SimplifiedSignature,
        importStrategy: ImportStrategy,
        scopeKind: KaScopeKind?,
    ): Boolean {
        return when (importStrategy) {
            ImportStrategy.DoNothing -> processSignature(simplifiedSignature, scopeKind)

            is ImportStrategy.InsertFqNameAndShorten -> processSignature(fullSimplifiedSignature, scopeKind)

            is ImportStrategy.AddImport -> {
                // `AddImport` doesn't necessarily mean that import is required and will be eventually inserted
                val considerContainer = scopeKind == null
                val shadowingCallableOrigin = processedSimplifiedSignatures[simplifiedSignature]
                if (shadowingCallableOrigin == null) {
                    // no callable with unspecified container shadows current callable
                    // if origin is `Index` and there is no shadowing callable,
                    // import is required and container needs to be considered
                    processSignature(
                        simplifiedSignature = if (considerContainer) fullSimplifiedSignature else simplifiedSignature,
                        scopeKind = scopeKind,
                    )
                } else {
                    if (!considerContainer) return true

                    // if the callable which shadows target callable belongs to the scope with priority lower than the priority of
                    // explicit simple importing scope, then it won't shadow target callable after import is inserted
                    when (shadowingCallableOrigin.get()) {
                        is KaScopeKind.PackageMemberScope,
                        is KaScopeKind.DefaultSimpleImportingScope,
                        is KaScopeKind.ExplicitStarImportingScope,
                        is KaScopeKind.DefaultStarImportingScope -> processSignature(fullSimplifiedSignature, scopeKind)

                        else -> true
                    }
                }
            }
        }
    }

    private fun processSignature(
        simplifiedSignature: SimplifiedSignature,
        scopeKind: KaScopeKind?,
    ): Boolean = processedSimplifiedSignatures.putIfAbsent(simplifiedSignature, Ref.create(scopeKind)) != null

    companion object {
        /**
         * If several extensions differ in receiver type only, then only one of them can be called without explicit cast. Other extensions
         * are expected to be filtered out by [ShadowedCallablesFilter]. For this to be done correctly, extensions need to be sorted by
         * their receiver type. Each extension corresponds to a receiver from the context, so extensions are sorted by:
         *
         * 1. The index of corresponding receiver from the context: if an extension corresponds to the last receiver, it can be shadowed
         * by an extension corresponding to the first receiver.
         * 2. The closeness of extension receiver type to the type of corresponding receiver in class hierarchy: if the receiver type of
         * extension is `Any`, it can be shadowed by an extension with more specific type.
         *
         * Also, extensions are sorted by their kind: a function variable can be shadowed by an extension function.
         */
        context(KaSession)
        fun sortExtensions(
            extensions: Collection<ApplicableExtension>,
            receiversFromContext: List<KaType>
        ): Collection<ApplicableExtension> {
            if (extensions.isEmpty()) return emptyList()

            val indexOfReceiverFromContext = mutableMapOf<ReceiverId, Int>()
            val indexInClassHierarchy = mutableMapOf<ReceiverId, Int>()

            for ((receiverFromContextIndex, receiverFromContextType) in receiversFromContext.withIndex()) {
                val selfWithSuperTypes = listOf(receiverFromContextType) + receiverFromContextType.allSupertypes
                for ((superTypeIndex, superType) in selfWithSuperTypes.withIndex()) {
                    val receiverId = ReceiverId.create(superType) ?: continue

                    indexOfReceiverFromContext.putIfAbsent(receiverId, receiverFromContextIndex)
                    indexInClassHierarchy.putIfAbsent(receiverId, superTypeIndex)
                }
            }

            return extensions
                .map { applicableExtension ->
                    val signature = applicableExtension.signature
                    val insertionStrategy = applicableExtension.insertionOptions.insertionStrategy
                    val receiverType = when {
                        signature is KaVariableSignature<*> && insertionStrategy is CallableInsertionStrategy.AsCall ->
                            (signature.returnType as? KaFunctionType)?.receiverType

                        else -> signature.receiverType
                    }
                    val receiverId = receiverType?.let { ReceiverId.create(it) }
                    applicableExtension to receiverId
                }
                .sortedWith(
                    compareBy(
                        { (_, receiverId) -> indexOfReceiverFromContext[receiverId] ?: Int.MAX_VALUE },
                        { (_, receiverId) -> indexInClassHierarchy[receiverId] ?: Int.MAX_VALUE },
                        { (applicableExtension, _) -> applicableExtension.signature is KaVariableSignature<*> }
                    ))
                .map { (applicableExtension, _) -> applicableExtension }
        }

        private sealed class ReceiverId {
            private data class ClassIdForNonLocal(val classId: ClassId) : ReceiverId()
            private data class NameForLocal(val name: Name) : ReceiverId()

            companion object {
                context(KaSession)
                fun create(type: KaType): ReceiverId? {
                    val expandedClassSymbol = type.expandedSymbol ?: return null
                    val name = expandedClassSymbol.name ?: return null

                    return when (val classId = expandedClassSymbol.classId) {
                        null -> NameForLocal(name)
                        else -> ClassIdForNonLocal(classId)
                    }
                }
            }
        }
    }
}


private sealed class SimplifiedSignature {

    abstract val name: Name

    /**
     * Container name is:
     * * [FqName] of package if the callable is a top-level declaration
     * * [FqName] of class if the callable is a non-local static member
     * * `null` otherwise
     *
     * Container name should be considered in the following cases:
     * * the fully qualified name of the callable can be inserted
     * * the callable requires import and isn't expected to be shadowed by any callables from scope
     */
    abstract val containerFqName: FqName?

    companion object {

        context(KaSymbolProvider)
        fun KaCallableSymbol.getContainerFqName(): FqName? {
            val callableId = callableId ?: return null
            return when (location) {
                // if a callable is in the root package, then its fully-qualified name coincides with short name
                KaSymbolLocation.TOP_LEVEL -> callableId.packageName.takeIf { !it.isRoot }
                KaSymbolLocation.CLASS -> {
                    val classId = callableId.classId ?: return null
                    val classKind = findClass(classId)?.classKind

                    classId.asSingleFqName().takeIf { classKind?.isObject == true }
                }

                else -> null
            }
        }
    }
}

private data class VariableLikeSimplifiedSignature(
    override val name: Name,
    override val containerFqName: FqName?,
) : SimplifiedSignature() {

    companion object {

        context(KaSymbolProvider)
        fun create(
            signature: KaVariableSignature<*>,
        ) = VariableLikeSimplifiedSignature(
            name = signature.name,
            containerFqName = signature.symbol.getContainerFqName(),
        )
    }
}

private class FunctionLikeSimplifiedSignature(
    override val name: Name,
    override val containerFqName: FqName?,
    private val requiredTypeArgumentsCount: Int,
    private val valueParameterTypes: Lazy<List<KaType>>,
    private val varargValueParameterIndices: List<Int>,
    private val typeRelationChecker: KaTypeRelationChecker,
) : SimplifiedSignature() {

    companion object {

        context(KaSession)
        fun create(
            signature: KaVariableSignature<*>,
        ) = FunctionLikeSimplifiedSignature(
            name = signature.name,
            containerFqName = signature.symbol.getContainerFqName(),
            requiredTypeArgumentsCount = 0,
            valueParameterTypes = lazy(LazyThreadSafetyMode.NONE) {
                val functionalType = signature.returnType
                if (functionalType !is KaFunctionType) error("Unexpected ${functionalType::class}")
                functionalType.parameterTypes
            },
            varargValueParameterIndices = emptyList(),
            typeRelationChecker = this@KaSession,
        )

        context(KaSession)
        fun create(
            signature: KaFunctionSignature<*>,
            requiresTypeArguments: (KaFunctionSymbol) -> Boolean,
        ): FunctionLikeSimplifiedSignature? {
            val symbol = signature.symbol as? KaNamedFunctionSymbol
                ?: return null

            val valueParameters = signature.valueParameters
            return FunctionLikeSimplifiedSignature(
                name = symbol.name,
                containerFqName = symbol.getContainerFqName(),
                requiredTypeArgumentsCount = if (requiresTypeArguments(symbol)) symbol.typeParameters.size else 0,
                valueParameterTypes = lazy(LazyThreadSafetyMode.NONE) {
                    listOfNotNull(symbol.receiverType) +
                            valueParameters.map { it.returnType }
                },
                varargValueParameterIndices = valueParameters.mapIndexedNotNull { index, parameter -> index.takeIf { parameter.symbol.isVararg } },
                typeRelationChecker = this@KaSession,
            )
        }
    }

    fun copy(containerFqName: FqName?) = FunctionLikeSimplifiedSignature(
        name = name,
        containerFqName = containerFqName,
        requiredTypeArgumentsCount = requiredTypeArgumentsCount,
        valueParameterTypes = valueParameterTypes,
        varargValueParameterIndices = varargValueParameterIndices,
        typeRelationChecker = typeRelationChecker,
    )

    override fun hashCode(): Int {
        var result = name.hashCode()
        result = 31 * result + containerFqName.hashCode()
        result = 31 * result + requiredTypeArgumentsCount.hashCode()
        result = 31 * result + varargValueParameterIndices.hashCode()
        return result
    }

    /**
     * We need to use semantic type equality instead of the default structural equality of [KaType] to check if two signatures overlap.
     */
    override fun equals(other: Any?): Boolean =
        this === other || other is FunctionLikeSimplifiedSignature
                && name == other.name
                && containerFqName == other.containerFqName
                && requiredTypeArgumentsCount == other.requiredTypeArgumentsCount
                && varargValueParameterIndices == other.varargValueParameterIndices
                && with(typeRelationChecker) {
            valueParameterTypes.value
                .all(other.valueParameterTypes.value) { (left, right) ->
                    left.semanticallyEquals(right)
                }
        }
}

private fun <T> List<T>.all(
    other: List<T>,
    predicate: (Pair<T, T>) -> Boolean,
): Boolean = size == other.size
        && zip(other).all(predicate)
