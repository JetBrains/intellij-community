// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.completion.contributors.helpers

import org.jetbrains.kotlin.analysis.api.KaExperimentalApi
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.components.KaScopeKind
import org.jetbrains.kotlin.analysis.api.signatures.KaCallableSignature
import org.jetbrains.kotlin.analysis.api.signatures.KaFunctionSignature
import org.jetbrains.kotlin.analysis.api.signatures.KaVariableSignature
import org.jetbrains.kotlin.analysis.api.symbols.KaCallableSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaSymbolLocation
import org.jetbrains.kotlin.analysis.api.symbols.markers.KaNamedSymbol
import org.jetbrains.kotlin.analysis.api.symbols.typeParameters
import org.jetbrains.kotlin.analysis.api.types.KaFunctionType
import org.jetbrains.kotlin.analysis.api.types.KaType
import org.jetbrains.kotlin.idea.completion.impl.k2.checkers.ApplicableExtension
import org.jetbrains.kotlin.idea.completion.lookups.CallableInsertionOptions
import org.jetbrains.kotlin.idea.completion.lookups.CallableInsertionStrategy
import org.jetbrains.kotlin.idea.completion.lookups.ImportStrategy
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name

internal class ShadowedCallablesFilter {
    data class FilterResult(val excludeFromCompletion: Boolean, val updatedInsertionOptions: CallableInsertionOptions)

    private val processedSignatures: MutableSet<KaCallableSignature<*>> = HashSet()
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
    context(KaSession)
    fun excludeFromCompletion(
        callable: KaCallableSignature<*>,
        options: CallableInsertionOptions,
        symbolOrigin: CompletionSymbolOrigin,
        isAlreadyImported: Boolean,
        typeArgumentsAreRequired: Boolean,
    ): FilterResult {
        // there is no need to create simplified signature if `KaCallableSignature<*>` is already processed
        if (!processedSignatures.add(callable)) return FilterResult(excludeFromCompletion = true, options)

        // if callable is already imported, try updating importing strategy
        if ((isAlreadyImported || symbolOrigin is CompletionSymbolOrigin.Scope)
            && options.importingStrategy != ImportStrategy.DoNothing
        ) {
            val updatedOptions = options.copy(importingStrategy = ImportStrategy.DoNothing)
            val excludeFromCompletion = processSignatureConsideringOptions(callable, updatedOptions, symbolOrigin, typeArgumentsAreRequired)
            if (!excludeFromCompletion) {
                return FilterResult(excludeFromCompletion = false, updatedOptions)
            }
        }

        return FilterResult(processSignatureConsideringOptions(callable, options, symbolOrigin, typeArgumentsAreRequired), options)
    }

    context(KaSession)
    private fun processSignatureConsideringOptions(
        callable: KaCallableSignature<*>,
        insertionOptions: CallableInsertionOptions,
        symbolOrigin: CompletionSymbolOrigin,
        typeArgumentsAreRequired: Boolean,
    ): Boolean {
        val (importingStrategy, insertionStrategy) = insertionOptions

        val isVariableCall = callable is KaVariableSignature<*> && insertionStrategy is CallableInsertionStrategy.AsCall

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
                            is KaScopeKind.PackageMemberScope,
                            is KaScopeKind.DefaultSimpleImportingScope,
                            is KaScopeKind.ExplicitStarImportingScope,
                            is KaScopeKind.DefaultStarImportingScope -> {
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

    context(KaSession)
    private fun processSignature(
        callable: KaCallableSignature<*>,
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
                .sortedWith(compareBy(
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
        context(KaSession)
        fun create(
            callableSignature: KaCallableSignature<*>,
            considerContainer: Boolean,
            isVariableCall: Boolean,
            typeArgumentsAreRequired: Boolean
        ): SimplifiedSignature? {
            val symbol = callableSignature.symbol
            if (symbol !is KaNamedSymbol) return null

            val containerFqName = if (considerContainer) symbol.getContainerFqName() else null

            @OptIn(KaExperimentalApi::class)
            return when (callableSignature) {
                is KaVariableSignature<*> -> createSimplifiedSignature(callableSignature, isVariableCall, containerFqName)
                is KaFunctionSignature<*> -> FunctionLikeSimplifiedSignature(
                    symbol.name,
                    containerFqName,
                    requiredTypeArgumentsCount = if (typeArgumentsAreRequired) callableSignature.symbol.typeParameters.size else 0,
                    lazy(LazyThreadSafetyMode.NONE) { callableSignature.valueParameters.map { it.returnType } },
                    callableSignature.valueParameters.mapIndexedNotNull { index, parameter -> index.takeIf { parameter.symbol.isVararg } },
                    this@KaSession,
                )
            }
        }

        context(KaSession)
        private fun createSimplifiedSignature(
            signature: KaVariableSignature<*>,
            isFunctionalVariableCall: Boolean,
            containerFqName: FqName?,
        ): SimplifiedSignature = when {
            isFunctionalVariableCall -> {
                FunctionLikeSimplifiedSignature(
                    signature.name,
                    containerFqName,
                    requiredTypeArgumentsCount = 0,
                    lazy(LazyThreadSafetyMode.NONE) {
                        val functionalType = signature.returnType as? KaFunctionType ?: error("Unexpected ${signature.returnType::class}")
                        functionalType.parameterTypes
                    },
                    varargValueParameterIndices = emptyList(),
                    this@KaSession,
                )
            }

            else -> VariableLikeSimplifiedSignature(signature.name, containerFqName)
        }

        context(KaSession)
        private fun KaCallableSymbol.getContainerFqName(): FqName? {
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
) : SimplifiedSignature()

private class FunctionLikeSimplifiedSignature(
    override val name: Name,
    override val containerFqName: FqName?,
    private val requiredTypeArgumentsCount: Int,
    private val valueParameterTypes: Lazy<List<KaType>>,
    private val varargValueParameterIndices: List<Int>,
    private val analysisSession: KaSession,
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
            areValueParameterTypesEqualTo(other)

    /**
     * We need to use semantic type equality instead of the default structural equality of [KaType] to check if two signatures overlap.
     */
    private fun areValueParameterTypesEqualTo(other: FunctionLikeSimplifiedSignature): Boolean {
        val types1 = other.valueParameterTypes.value
        val types2 = valueParameterTypes.value
        if (types1.size != types2.size) return false

        with(analysisSession) {
            for (i in types1.indices) {
                if (!types1[i].semanticallyEquals(types2[i])) return false
            }
            return true
        }
    }
}
