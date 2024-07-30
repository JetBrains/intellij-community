// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.imports

import com.intellij.openapi.progress.ProgressIndicatorProvider
import com.intellij.psi.ContributedReferenceHost
import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.resolution.*
import org.jetbrains.kotlin.analysis.api.symbols.*
import org.jetbrains.kotlin.analysis.api.types.KaType
import org.jetbrains.kotlin.analysis.api.types.symbol
import org.jetbrains.kotlin.idea.references.KDocReference
import org.jetbrains.kotlin.idea.references.KtInvokeFunctionReference
import org.jetbrains.kotlin.idea.references.KtReference
import org.jetbrains.kotlin.idea.references.mainReference
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.name.withClassId
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.getReceiverExpression
import kotlin.collections.isNotEmpty
import kotlin.collections.orEmpty
import kotlin.collections.plusAssign

internal class UsedReferencesCollector(private val file: KtFile) {

    data class Result(
        val usedDeclarations: Map<FqName, Set<Name>>,
        val unresolvedNames: Set<Name>,
    )

    private val unresolvedNames: HashSet<Name> = hashSetOf()
    private val usedDeclarations: HashMap<FqName, MutableSet<Name>> = hashMapOf()

    private val aliases: Map<FqName, List<Name>> = collectImportAliases(file)

    fun KaSession.collectUsedReferences(): Result {
        file.accept(object : KtVisitorVoid() {
            override fun visitElement(element: PsiElement) {
                ProgressIndicatorProvider.checkCanceled()
                element.acceptChildren(this)
            }

            override fun visitImportList(importList: KtImportList) {}

            override fun visitPackageDirective(directive: KtPackageDirective) {}

            override fun visitKtElement(element: KtElement) {
                super.visitKtElement(element)

                collectReferencesFrom(element)
            }
        })

        return Result(usedDeclarations, unresolvedNames)
    }

    private fun KaSession.collectReferencesFrom(element: KtElement) {
        // we ignore such elements because resolving them leads to UAST resolution,
        // and that in turn leads to KT-68601 when import optimization is called after move refactoring
        if (element is ContributedReferenceHost) return

        if (element is KtLabelReferenceExpression) return

        val references = element.references
        if (references.isEmpty()) return

        for (reference in references) {
            ProgressIndicatorProvider.checkCanceled()

            if (reference !is KtReference) continue
            if (isEmptyInvokeReference(reference)) continue

            val resolvedSymbols = reference.resolveToSymbols()
            val isResolved = resolvedSymbols.isNotEmpty()

            ProgressIndicatorProvider.checkCanceled()

            val dispatchReceiver = resolveDispatchReceiver(element)
            if (dispatchReceiver != null && isDispatchedCall(element, dispatchReceiver, file)) {
                continue
            }

            ProgressIndicatorProvider.checkCanceled()

            val names = reference.resolvesByNames
            if (!isResolved) {
                unresolvedNames += names
                continue
            }

            val importableSymbols = resolvedSymbols.mapNotNull { toImportableSymbol(it, reference, file) }

            for (symbol in importableSymbols) {
                if (!canBeResolvedViaImport(reference, symbol)) continue

                val importableName = computeImportableName(symbol, dispatchReceiver as? KaImplicitReceiverValue) ?: continue

                ProgressIndicatorProvider.checkCanceled()

                val newNames = (aliases[importableName].orEmpty() + importableName.shortName()).intersect(names)
                usedDeclarations.getOrPut(importableName) { hashSetOf() } += newNames
            }
        }
    }
}

/**
 * In K2, every call in the form of `foo()` has `KtInvokeFunctionReference` on it.
 *
 * In the cases when `foo()` call is not actually an `invoke` call, we do not want to process such references,
 * since they are not supposed to resolve anywhere.
 */
private fun KaSession.isEmptyInvokeReference(reference: KtReference): Boolean {
    if (reference !is KtInvokeFunctionReference) return false

    val callInfo = reference.element.resolveToCall()
    val isImplicitInvoke = callInfo?.calls?.any { it is KaSimpleFunctionCall && it.isImplicitInvoke } == true

    return !isImplicitInvoke
}

private fun KaSession.toImportableSymbol(
    target: KaSymbol,
    reference: KtReference,
    containingFile: KtFile = reference.element.containingKtFile,
): KaSymbol? = when {
    target is KaReceiverParameterSymbol -> null

    reference.isImplicitReferenceToCompanion() -> {
        (target as? KaNamedClassSymbol)?.containingSymbol
    }

    target is KaConstructorSymbol -> {
        val targetClass = target.containingSymbol as? KaClassLikeSymbol

        // if constructor is typealiased, it can be imported in any scenario
        val typeAlias = targetClass?.let { resolveTypeAliasedConstructorReference(reference, it, containingFile) }

        // if constructor leads to inner class, it cannot be resolved by import
        val notInnerTargetClass = targetClass?.takeUnless { it is KaNamedClassSymbol && it.isInner }

        typeAlias ?: notInnerTargetClass
    }

    target is KaSamConstructorSymbol -> {
        val targetClass = findSamClassFor(target)

        targetClass?.let { resolveTypeAliasedConstructorReference(reference, it, containingFile) } ?: targetClass
    }

    else -> target
}

/**
 * We want to skipp the calls which require implicit receiver to be dispatched.
 */
private fun KaSession.isDispatchedCall(
    element: KtElement,
    dispatchReceiver: KaReceiverValue,
    containingFile: KtFile = element.containingKtFile
): Boolean {
    return when (dispatchReceiver) {
        is KaExplicitReceiverValue -> true

        is KaSmartCastedReceiverValue -> isDispatchedCall(element, dispatchReceiver.original, containingFile)

        is KaImplicitReceiverValue -> !isStaticallyImportedReceiver(element, dispatchReceiver, containingFile)
    }
}

/**
 * Checks if [implicitDispatchReceiver] is introduced via static import
 * from Kotlin object or Java class.
 */
private fun KaSession.isStaticallyImportedReceiver(
    element: KtElement,
    implicitDispatchReceiver: KaImplicitReceiverValue,
    containingFile: KtFile
): Boolean {
    val receiverTypeSymbol = implicitDispatchReceiver.type.symbol ?: return false
    val receiverIsObject = receiverTypeSymbol is KaClassSymbol && receiverTypeSymbol.classKind.isObject

    // with static imports, the implicit receiver is either some object symbol or `Unit` in case of imports from Java classes
    if (!receiverIsObject) return false
    val regularImplicitReceivers = containingFile.scopeContext(element).implicitReceivers

    return regularImplicitReceivers.none { it.type.semanticallyEquals(implicitDispatchReceiver.type) }
}

private fun KaSession.resolveDispatchReceiver(element: KtElement): KaReceiverValue? {
    val adjustedElement = element.callableReferenceExpressionForCallableReference() ?: element
    val dispatchReceiver = adjustedElement.resolveToCall()?.singleCallOrNull<KaCallableMemberCall<*, *>>()?.partiallyAppliedSymbol?.dispatchReceiver

    return dispatchReceiver
}

/**
 * Finds the original SAM type by the [samConstructorSymbol].
 *
 * A workaround for the KT-70301.
 */
private fun KaSession.findSamClassFor(samConstructorSymbol: KaSamConstructorSymbol): KaClassSymbol? {
    val samCallableId = samConstructorSymbol.callableId ?: return null
    if (samCallableId.isLocal) return null

    val samClassId = ClassId.fromString(samCallableId.toString())

    return findClass(samClassId)
}

/**
 * Takes a [reference] pointing to a typealiased constructor call like `FooAlias()`,
 * and [expandedClassSymbol] pointing to the expanded class `Foo`.
 *
 * Returns a `FooAlias` typealias symbol if it is resolvable at this position, and `null` otherwise.
 *
 * This is a workaround function until KTIJ-26098 is fixed.
 */
private fun KaSession.resolveTypeAliasedConstructorReference(
    reference: KtReference,
    expandedClassSymbol: KaClassLikeSymbol,
    containingFile: KtFile,
): KaClassLikeSymbol? {
    val originalReferenceName = reference.resolvesByNames.singleOrNull() ?: return null

    // optimization to avoid resolving typealiases which are not available
    if (!typeAliasIsAvailable(originalReferenceName, containingFile)) return null

    val referencedType = resolveReferencedType(reference) ?: return null
    if (referencedType.symbol != expandedClassSymbol) return null

    val typealiasType = referencedType.abbreviation ?: return null

    return typealiasType.symbol
}

private fun KaSession.typeAliasIsAvailable(name: Name, containingFile: KtFile): Boolean {
    val importingScope = containingFile.importingScopeContext
    val foundClassifiers = importingScope.compositeScope().classifiers(name)

    return foundClassifiers.any { it is KaTypeAliasSymbol }
}

private fun KaSession.resolveReferencedType(reference: KtReference): KaType? {
    val originalReferenceName = reference.resolvesByNames.singleOrNull() ?: return null

    val psiFactory = KtPsiFactory.contextual(reference.element)
    val psiType = psiFactory.createTypeCodeFragment(originalReferenceName.asString(), context = reference.element).getContentElement()

    return psiType?.type
}

private fun collectImportAliases(file: KtFile): Map<FqName, List<Name>> = if (file.hasImportAlias()) {
    file.importDirectives
        .asSequence()
        .filter { !it.isAllUnder && it.alias != null }
        .mapNotNull { it.importPath }
        .groupBy(keySelector = { it.fqName }, valueTransform = { it.importedName as Name })
} else {
    emptyMap()
}

private fun KaSession.computeImportableName(
    target: KaSymbol,
    implicitDispatchReceiver: KaImplicitReceiverValue? // TODO: support other types of dispatcher values
): FqName? {
    if (implicitDispatchReceiver == null) {
        return target.importableFqName
    }

    if (target !is KaCallableSymbol) return null

    val callableId = target.callableId ?: return null
    if (callableId.classId == null) return null

    val implicitReceiver = implicitDispatchReceiver.symbol as? KaClassLikeSymbol ?: return null
    val implicitReceiverClassId = implicitReceiver.classId ?: return null

    val substitutedCallableId = callableId.withClassId(implicitReceiverClassId)

    return substitutedCallableId.asSingleFqName()
}

private fun KaSession.canBeResolvedViaImport(reference: KtReference, target: KaSymbol): Boolean {
    if (reference is KDocReference) {
        return canBeResolvedViaImport(reference, target)
    }

    if (target is KaCallableSymbol && target.isExtension) {
        return true
    }

    val referenceExpression = reference.element as? KtNameReferenceExpression

    val explicitReceiver = referenceExpression?.getReceiverExpression()
        ?: referenceExpression?.callableReferenceExpressionForCallableReference()?.receiverExpression

    if (explicitReceiver != null) {
        val extensionReceiver = resolveExtensionReceiverForFunctionalTypeVariable(referenceExpression, target)
        return extensionReceiver?.expression == explicitReceiver
    }

    return true
}

private fun KaSession.resolveExtensionReceiverForFunctionalTypeVariable(
    referenceExpression: KtNameReferenceExpression?,
    target: KaSymbol,
): KaExplicitReceiverValue? {
    val parentCall = referenceExpression?.parent as? KtCallExpression
    val isFunctionalTypeVariable = target is KaPropertySymbol && target.returnType.let { it.isFunctionType || it.isSuspendFunctionType }

    if (parentCall == null || !isFunctionalTypeVariable) {
        return null
    }

    val parentCallInfo = parentCall.resolveToCall()?.singleCallOrNull<KaSimpleFunctionCall>() ?: return null
    if (!parentCallInfo.isImplicitInvoke) return null

    return parentCallInfo.partiallyAppliedSymbol.extensionReceiver as? KaExplicitReceiverValue
}

private fun KaSession.canBeResolvedViaImport(reference: KDocReference, target: KaSymbol): Boolean {
    val qualifier = reference.element.getQualifier() ?: return true

    return if (target is KaCallableSymbol && target.isExtension) {
        val elementHasFunctionDescriptor = reference.element.mainReference.resolveToSymbols().any { it is KaFunctionSymbol }
        val qualifierHasClassDescriptor = qualifier.mainReference.resolveToSymbols().any { it is KaClassLikeSymbol }
        elementHasFunctionDescriptor && qualifierHasClassDescriptor
    } else {
        false
    }
}

private fun KtElement.callableReferenceExpressionForCallableReference(): KtCallableReferenceExpression? =
    (parent as? KtCallableReferenceExpression)?.takeIf { it.callableReference == this }