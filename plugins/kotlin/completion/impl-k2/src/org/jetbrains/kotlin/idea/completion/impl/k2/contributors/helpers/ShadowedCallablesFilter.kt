// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.completion.contributors.helpers

import org.jetbrains.kotlin.analysis.api.KtAnalysisSession
import org.jetbrains.kotlin.analysis.api.components.KtScopeKind
import org.jetbrains.kotlin.analysis.api.signatures.KtCallableSignature
import org.jetbrains.kotlin.analysis.api.signatures.KtFunctionLikeSignature
import org.jetbrains.kotlin.analysis.api.signatures.KtVariableLikeSignature
import org.jetbrains.kotlin.analysis.api.symbols.KtCallableSymbol
import org.jetbrains.kotlin.analysis.api.symbols.markers.KtNamedSymbol
import org.jetbrains.kotlin.analysis.api.symbols.markers.KtSymbolKind
import org.jetbrains.kotlin.analysis.api.types.KtFunctionalType
import org.jetbrains.kotlin.analysis.api.types.KtType
import org.jetbrains.kotlin.idea.completion.checkers.ApplicableExtension
import org.jetbrains.kotlin.idea.completion.lookups.CallableInsertionOptions
import org.jetbrains.kotlin.idea.completion.lookups.CallableInsertionStrategy
import org.jetbrains.kotlin.idea.completion.lookups.ImportStrategy
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name

internal class ShadowedCallablesFilter {
    data class FilterResult(val excludeFromCompletion: Boolean, val updatedInsertionOptions: CallableInsertionOptions)

    private val processedSignatures: MutableSet<KtCallableSignature<*>> = HashSet()
    private val processedSimplifiedSignatures: MutableMap<SimplifiedSignature, CompletionSymbolOrigin> = HashMap()

    /**
     *  Checks whether callable is shadowed and updates [CallableInsertionOptions] if the callable is already imported and its short name
     *  can be used without being shadowed.
     *
     *  When the fully qualified function name is inserted, [org.jetbrains.kotlin.idea.base.analysis.api.utils.shortenReferences] is invoked
     *  on the call with missing arguments and shortened version is resolved to a different symbol, which leads to shortening not being
     *  invoked. For example, `kotlin.text.String()` is not shortened by reference shortener, because shortened version `String()`
     *  is resolved to `kotlin.String()`. That's why we can't rely on reference shortener and need to use [ImportStrategy.DoNothing].
     */
    context(KtAnalysisSession)
    fun excludeFromCompletion(
        callable: KtCallableSignature<*>,
        options: CallableInsertionOptions,
        symbolOrigin: CompletionSymbolOrigin,
        isAlreadyImported: Boolean,
        typeArgumentsAreRequired: Boolean,
    ): FilterResult {
        // there is no need to create simplified signature if `KtCallableSignature<*>` is already processed
        if (callable in processedSignatures) return FilterResult(excludeFromCompletion = true, options)
        processedSignatures.add(callable)

        val importingStrategy = options.importingStrategy
        val updatedImportingStrategy = ImportStrategy.DoNothing

        // if callable is already imported, try updating importing strategy
        if ((isAlreadyImported || symbolOrigin is CompletionSymbolOrigin.Scope) && importingStrategy != updatedImportingStrategy) {
            val updatedOptions = options.withImportingStrategy(updatedImportingStrategy)
            val excludeFromCompletion = processSignatureConsideringOptions(callable, updatedOptions, symbolOrigin, typeArgumentsAreRequired)
            if (!excludeFromCompletion) {
                return FilterResult(excludeFromCompletion, updatedOptions)
            }
        }

        return FilterResult(processSignatureConsideringOptions(callable, options, symbolOrigin, typeArgumentsAreRequired), options)
    }

    context(KtAnalysisSession)
    private fun processSignatureConsideringOptions(
        callable: KtCallableSignature<*>,
        insertionOptions: CallableInsertionOptions,
        symbolOrigin: CompletionSymbolOrigin,
        typeArgumentsAreRequired: Boolean,
    ): Boolean {
        val (importingStrategy, insertionStrategy) = insertionOptions

        val isVariableCall = callable is KtVariableLikeSignature<*> && insertionStrategy is CallableInsertionStrategy.AsCall

        return when (importingStrategy) {
            is ImportStrategy.DoNothing ->
                processSignature(callable, symbolOrigin, considerContainer = false, isVariableCall, typeArgumentsAreRequired)

            is ImportStrategy.AddImport -> { // `AddImport` doesn't necessarily mean that import is required and will be eventually inserted
                val simplifiedSignature = SimplifiedSignature.create(
                    callable,
                    considerContainer = false,
                    isVariableCall,
                    typeArgumentsAreRequired
                ) ?: return false

                when (val shadowingCallableOrigin = processedSimplifiedSignatures[simplifiedSignature]) {
                    // no callable with unspecified container shadows current callable
                    null -> {
                        // if origin is `Index` and there is no shadowing callable, import is required and container needs to be considered
                        val considerContainer = symbolOrigin is CompletionSymbolOrigin.Index
                        processSignature(callable, symbolOrigin, considerContainer, isVariableCall, typeArgumentsAreRequired)
                    }

                    else -> {
                        if (symbolOrigin !is CompletionSymbolOrigin.Index) return true

                        // if the callable which shadows target callable belongs to the scope with priority lower than the priority of
                        // explicit simple importing scope, then it won't shadow target callable after import is inserted
                        when ((shadowingCallableOrigin as? CompletionSymbolOrigin.Scope)?.kind) {
                            is KtScopeKind.PackageMemberScope,
                            is KtScopeKind.DefaultSimpleImportingScope,
                            is KtScopeKind.ExplicitStarImportingScope,
                            is KtScopeKind.DefaultStarImportingScope -> {
                                processSignature(callable, symbolOrigin, considerContainer = true, isVariableCall, typeArgumentsAreRequired)
                            }

                            else -> true
                        }
                    }
                }
            }

            is ImportStrategy.InsertFqNameAndShorten ->
                processSignature(callable, symbolOrigin, considerContainer = true, isVariableCall, typeArgumentsAreRequired)
        }
    }

    context(KtAnalysisSession)
    private fun processSignature(
        callable: KtCallableSignature<*>,
        symbolOrigin: CompletionSymbolOrigin,
        considerContainer: Boolean,
        isVariableCall: Boolean,
        typeArgumentsAreRequired: Boolean,
    ): Boolean {
        val simplifiedSignature = SimplifiedSignature.create(
            callable,
            considerContainer,
            isVariableCall,
            typeArgumentsAreRequired
        ) ?: return false
        if (simplifiedSignature in processedSimplifiedSignatures) return true

        processedSimplifiedSignatures[simplifiedSignature] = symbolOrigin
        return false
    }

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
        context(KtAnalysisSession)
        fun sortExtensions(
            extensions: Collection<ApplicableExtension>,
            receiversFromContext: List<KtType>
        ): Collection<ApplicableExtension> {
            if (extensions.isEmpty()) return emptyList()

            val indexOfReceiverFromContext = mutableMapOf<ReceiverId, Int>()
            val indexInClassHierarchy = mutableMapOf<ReceiverId, Int>()

            for ((receiverFromContextIndex, receiverFromContextType) in receiversFromContext.withIndex()) {
                val selfWithSuperTypes = listOf(receiverFromContextType) + receiverFromContextType.getAllSuperTypes()
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
                        signature is KtVariableLikeSignature<*> && insertionStrategy is CallableInsertionStrategy.AsCall ->
                            (signature.returnType as? KtFunctionalType)?.receiverType

                        else -> signature.receiverType
                    }
                    val receiverId = receiverType?.let { ReceiverId.create(it) }
                    applicableExtension to receiverId
                }
                .sortedWith(compareBy(
                    { (_, receiverId) -> indexOfReceiverFromContext[receiverId] ?: Int.MAX_VALUE },
                    { (_, receiverId) -> indexInClassHierarchy[receiverId] ?: Int.MAX_VALUE },
                    { (applicableExtension, _) -> applicableExtension.signature is KtVariableLikeSignature<*> }
                ))
                .map { (applicableExtension, _) -> applicableExtension }
        }

        private sealed class ReceiverId {
            private data class ClassIdForNonLocal(val classId: ClassId) : ReceiverId()
            private data class NameForLocal(val name: Name) : ReceiverId()

            companion object {
                context(KtAnalysisSession)
                fun create(type: KtType): ReceiverId? {
                    val expandedClassSymbol = type.expandedClassSymbol ?: return null
                    val name = expandedClassSymbol.name ?: return null

                    return when (val classId = expandedClassSymbol.classIdIfNonLocal) {
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
        context(KtAnalysisSession)
        fun create(
            callableSignature: KtCallableSignature<*>,
            considerContainer: Boolean,
            isVariableCall: Boolean,
            typeArgumentsAreRequired: Boolean
        ): SimplifiedSignature? {
            val symbol = callableSignature.symbol
            if (symbol !is KtNamedSymbol) return null

            val containerFqName = if (considerContainer) symbol.getContainerFqName() else null

            return when (callableSignature) {
                is KtVariableLikeSignature<*> -> createSimplifiedSignature(callableSignature, isVariableCall, containerFqName)
                is KtFunctionLikeSignature<*> -> FunctionLikeSimplifiedSignature(
                    symbol.name,
                    containerFqName,
                    requiredTypeArgumentsCount = if (typeArgumentsAreRequired) callableSignature.symbol.typeParameters.size else 0,
                    lazy(LazyThreadSafetyMode.NONE) { callableSignature.valueParameters.map { it.returnType } },
                    callableSignature.valueParameters.mapIndexedNotNull { index, parameter -> index.takeIf { parameter.symbol.isVararg } },
                )
            }
        }

        context(KtAnalysisSession)
        private fun createSimplifiedSignature(
            signature: KtVariableLikeSignature<*>,
            isFunctionalVariableCall: Boolean,
            containerFqName: FqName?,
        ): SimplifiedSignature = when {
            isFunctionalVariableCall -> {
                FunctionLikeSimplifiedSignature(
                    signature.name,
                    containerFqName,
                    requiredTypeArgumentsCount = 0,
                    lazy(LazyThreadSafetyMode.NONE) {
                        val functionalType = signature.returnType as? KtFunctionalType ?: error("Unexpected ${signature.returnType::class}")
                        functionalType.parameterTypes
                    },
                    varargValueParameterIndices = emptyList()
                )
            }

            else -> VariableLikeSimplifiedSignature(signature.name, containerFqName)
        }

        context(KtAnalysisSession)
        private fun KtCallableSymbol.getContainerFqName(): FqName? {
            val callableId = callableIdIfNonLocal ?: return null
            return when (symbolKind) {
                // if a callable is in the root package, then its fully-qualified name coincides with short name
                KtSymbolKind.TOP_LEVEL -> callableId.packageName.takeIf { !it.isRoot }
                KtSymbolKind.CLASS_MEMBER -> {
                    val classId = callableId.classId ?: return null
                    val classKind = getClassOrObjectSymbolByClassId(classId)?.classKind

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
) : SimplifiedSignature()

private class FunctionLikeSimplifiedSignature(
    override val name: Name,
    override val containerFqName: FqName?,
    private val requiredTypeArgumentsCount: Int,
    private val valueParameterTypes: Lazy<List<KtType>>,
    private val varargValueParameterIndices: List<Int>,
) : SimplifiedSignature() {
    override fun hashCode(): Int {
        var result = name.hashCode()
        result = 31 * result + containerFqName.hashCode()
        result = 31 * result + requiredTypeArgumentsCount.hashCode()
        result = 31 * result + varargValueParameterIndices.hashCode()
        return result
    }

    override fun equals(other: Any?): Boolean = this === other ||
            other is FunctionLikeSimplifiedSignature &&
            other.name == name &&
            other.containerFqName == containerFqName &&
            other.requiredTypeArgumentsCount == requiredTypeArgumentsCount &&
            other.varargValueParameterIndices == varargValueParameterIndices &&
            other.valueParameterTypes.value == valueParameterTypes.value
}
