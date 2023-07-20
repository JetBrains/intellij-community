// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.completion.contributors.helpers

import org.jetbrains.kotlin.analysis.api.KtAnalysisSession
import org.jetbrains.kotlin.analysis.api.components.KtExtensionApplicabilityResult
import org.jetbrains.kotlin.analysis.api.components.KtExtensionApplicabilityResult.ApplicableAsExtensionCallable
import org.jetbrains.kotlin.analysis.api.components.KtExtensionApplicabilityResult.ApplicableAsFunctionalVariableCall
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
    private val processedSignatures: MutableMap<SimplifiedSignature, CompletionSymbolOrigin> = mutableMapOf()

    context(KtAnalysisSession)
    fun excludeFromCompletion(
        callable: KtCallableSignature<*>,
        insertionOptions: CallableInsertionOptions,
        symbolOrigin: CompletionSymbolOrigin,
    ): Boolean {
        val (importingStrategy, insertionStrategy) = insertionOptions

        val isVariableCall = callable is KtVariableLikeSignature<*> && insertionStrategy is CallableInsertionStrategy.AsCall

        return when (importingStrategy) {
            is ImportStrategy.DoNothing -> processSignature(callable, symbolOrigin, considerContainer = false, isVariableCall)

            is ImportStrategy.AddImport -> { // `AddImport` doesn't necessarily mean that import is required and will be eventually inserted
                val simplifiedSignature = SimplifiedSignature.create(callable, considerContainer = false, isVariableCall) ?: return false

                when (val shadowingCallableOrigin = processedSignatures[simplifiedSignature]) {
                    // no callable with unspecified container shadows current callable
                    null -> {
                        // if origin is `Index` and there is no shadowing callable, import is required and container needs to be considered
                        val considerContainer = symbolOrigin is CompletionSymbolOrigin.Index
                        processSignature(callable, symbolOrigin, considerContainer, isVariableCall)
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
                                processSignature(callable, symbolOrigin, considerContainer = true, isVariableCall)
                            }

                            else -> true
                        }
                    }
                }
            }

            is ImportStrategy.InsertFqNameAndShorten -> processSignature(callable, symbolOrigin, considerContainer = true, isVariableCall)
        }
    }

    context(KtAnalysisSession)
    private fun processSignature(
        callable: KtCallableSignature<*>,
        symbolOrigin: CompletionSymbolOrigin,
        considerContainer: Boolean,
        isVariableCall: Boolean
    ): Boolean {
        val simplifiedSignature = SimplifiedSignature.create(callable, considerContainer, isVariableCall) ?: return false
        if (simplifiedSignature in processedSignatures) return true

        processedSignatures[simplifiedSignature] = symbolOrigin
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
                    val (signature, applicabilityResult) = applicableExtension
                    val receiverType = when (applicabilityResult) {
                        is ApplicableAsExtensionCallable -> signature.receiverType
                        is ApplicableAsFunctionalVariableCall -> (signature.returnType as? KtFunctionalType)?.receiverType
                    }
                    val receiverId = receiverType?.let { ReceiverId.create(it) }
                    applicableExtension to receiverId
                }
                .sortedWith(compareBy(
                    { (_, receiverId) -> indexOfReceiverFromContext[receiverId] ?: Int.MAX_VALUE },
                    { (_, receiverId) -> indexInClassHierarchy[receiverId] ?: Int.MAX_VALUE },
                    { (applicableExtension, _) -> applicableExtension.applicabilityResult is ApplicableAsFunctionalVariableCall }
                ))
                .map { (applicableExtension, _) -> applicableExtension }
        }

        private sealed class ReceiverId {
            private data class ClassIdForNonLocal(val classId: ClassId): ReceiverId()
            private data class NameForLocal(val name: Name): ReceiverId()

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
        fun create(callableSignature: KtCallableSignature<*>, considerContainer: Boolean, isVariableCall: Boolean): SimplifiedSignature? {
            val symbol = callableSignature.symbol
            if (symbol !is KtNamedSymbol) return null

            val containerFqName = if (considerContainer) symbol.getContainerFqName() else null

            return when (callableSignature) {
                is KtVariableLikeSignature<*> -> createSimplifiedSignature(callableSignature, isVariableCall, containerFqName)
                is KtFunctionLikeSignature<*> -> FunctionLikeSimplifiedSignature(
                    symbol.name,
                    containerFqName,
                    typeParametersCount = callableSignature.symbol.typeParameters.size,
                    callableSignature.valueParameters.map { it.returnType },
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
                val functionalType = signature.returnType as? KtFunctionalType ?: error("Unexpected ${signature.returnType::class}")
                FunctionLikeSimplifiedSignature(
                    signature.name,
                    containerFqName,
                    typeParametersCount = 0,
                    functionalType.parameterTypes,
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

private data class FunctionLikeSimplifiedSignature(
    override val name: Name,
    override val containerFqName: FqName?,
    val typeParametersCount: Int,
    val valueParameterTypes: List<KtType>,
    val varargValueParameterIndices: List<Int>,
) : SimplifiedSignature()
