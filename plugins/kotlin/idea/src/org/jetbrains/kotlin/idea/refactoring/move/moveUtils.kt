// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.refactoring.move

import com.intellij.psi.PsiMember
import com.intellij.psi.PsiModifier
import com.intellij.psi.PsiReference
import com.intellij.usageView.UsageInfo
import org.jetbrains.kotlin.descriptors.*
import org.jetbrains.kotlin.idea.base.utils.fqname.isImported
import org.jetbrains.kotlin.idea.caches.resolve.analyzeWithAllCompilerChecks
import org.jetbrains.kotlin.idea.codeInsight.DescriptorToSourceUtilsIde
import org.jetbrains.kotlin.idea.imports.importableFqName
import org.jetbrains.kotlin.idea.references.KtSimpleNameReference
import org.jetbrains.kotlin.idea.references.mainReference
import org.jetbrains.kotlin.idea.statistics.KotlinMoveRefactoringFUSCollector
import org.jetbrains.kotlin.load.java.descriptors.JavaCallableMemberDescriptor
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.containingClassOrObject
import org.jetbrains.kotlin.psi.psiUtil.forEachDescendantOfType
import org.jetbrains.kotlin.psi.psiUtil.getParentOfTypeAndBranch
import org.jetbrains.kotlin.resolve.BindingContext
import org.jetbrains.kotlin.resolve.DescriptorUtils
import org.jetbrains.kotlin.resolve.calls.util.getResolvedCall
import org.jetbrains.kotlin.resolve.descriptorUtil.getImportableDescriptor
import org.jetbrains.kotlin.resolve.descriptorUtil.isCompanionObject
import org.jetbrains.kotlin.resolve.descriptorUtil.parents
import org.jetbrains.kotlin.resolve.scopes.receivers.ImplicitClassReceiver
import org.jetbrains.kotlin.utils.addIfNotNull
import java.lang.System.currentTimeMillis


fun KtElement.getInternalReferencesToUpdateOnPackageNameChange(containerChangeInfo: MoveContainerChangeInfo): List<UsageInfo> {
    val usages = ArrayList<UsageInfo>()
    processInternalReferencesToUpdateOnPackageNameChange(containerChangeInfo) { expr, factory -> usages.addIfNotNull(factory(expr)) }
    return usages
}

private typealias UsageInfoFactory = (KtSimpleNameExpression) -> UsageInfo?

fun KtElement.processInternalReferencesToUpdateOnPackageNameChange(
    containerChangeInfo: MoveContainerChangeInfo,
    body: (originalRefExpr: KtSimpleNameExpression, usageFactory: UsageInfoFactory) -> Unit
) {
    val file = containingFile as? KtFile ?: return

    val importPaths = file.importDirectives.mapNotNull { it.importPath }

    tailrec fun isImported(descriptor: DeclarationDescriptor): Boolean {
        val fqName = DescriptorUtils.getFqName(descriptor).let { if (it.isSafe) it.toSafe() else return@isImported false }
        if (importPaths.any { fqName.isImported(it, false) }) return true

        return when (val containingDescriptor = descriptor.containingDeclaration) {
            is ClassDescriptor, is PackageViewDescriptor -> isImported(containingDescriptor)
            else -> false
        }
    }

    fun MoveContainerInfo.matches(decl: DeclarationDescriptor) = when(this) {
        is MoveContainerInfo.UnknownPackage -> decl is PackageViewDescriptor && decl.fqName == fqName
        is MoveContainerInfo.Package -> decl is PackageFragmentDescriptor && decl.fqName == fqName
        is MoveContainerInfo.Class -> decl is ClassDescriptor && decl.importableFqName == fqName
    }

    fun processReference(refExpr: KtSimpleNameExpression, bindingContext: BindingContext): (UsageInfoFactory)? {
        val descriptor = bindingContext[BindingContext.REFERENCE_TARGET, refExpr]?.getImportableDescriptor() ?: return null
        val containingDescriptor = descriptor.containingDeclaration ?: return null

        val callableKind = (descriptor as? CallableMemberDescriptor)?.kind
        if (callableKind != null && callableKind != CallableMemberDescriptor.Kind.DECLARATION) return null

        // Special case for enum entry superclass references (they have empty text and don't need to be processed by the refactoring)
        if (refExpr.textRange.isEmpty) return null

        if (descriptor is ClassDescriptor && descriptor.isInner && refExpr.parent is KtCallExpression) return null

        val isCallable = descriptor is CallableDescriptor
        val isExtension = isCallable && KotlinMoveRefactoringSupport.getInstance().isExtensionRef(refExpr)
        val isCallableReference = isCallableReference(refExpr.mainReference)

        val declaration by lazy {
            var result = DescriptorToSourceUtilsIde.getAnyDeclaration(project, descriptor) ?: return@lazy null

            if (descriptor.isCompanionObject() &&
                bindingContext[BindingContext.SHORT_REFERENCE_TO_COMPANION_OBJECT, refExpr] !== null
            ) {
                result = (result as? KtObjectDeclaration)?.containingClassOrObject ?: result
            }

            result
        }

        if (isCallable) {
            if (!isCallableReference) {
                if (isExtension && containingDescriptor is ClassDescriptor) {
                    val dispatchReceiver = refExpr.getResolvedCall(bindingContext)?.dispatchReceiver
                    val implicitClass = (dispatchReceiver as? ImplicitClassReceiver)?.classDescriptor
                    if (implicitClass?.isCompanionObject == true) {
                        return { ImplicitCompanionAsDispatchReceiverUsageInfo(it, implicitClass) }
                    }
                    if (dispatchReceiver != null || containingDescriptor.kind != ClassKind.OBJECT) return null
                }
            }

            if (!isExtension) {
                val isCompatibleDescriptor = containingDescriptor is PackageFragmentDescriptor ||
                        containingDescriptor is ClassDescriptor && containingDescriptor.kind == ClassKind.OBJECT ||
                        descriptor is JavaCallableMemberDescriptor && ((declaration as? PsiMember)?.hasModifierProperty(PsiModifier.STATIC) == true)
                if (!isCompatibleDescriptor) return null
            }
        }

        if (!DescriptorUtils.getFqName(descriptor).isSafe) return null

        val (oldContainer, newContainer) = containerChangeInfo

        val containerFqName = descriptor.parents.mapNotNull {
            when {
                oldContainer.matches(it) -> oldContainer.fqName
                newContainer.matches(it) -> newContainer.fqName
                else -> null
            }
        }.firstOrNull()

        val isImported = isImported(descriptor)
        if (isImported && this is KtFile) return null

        val declarationNotNull = declaration ?: return null

        if (isExtension || containerFqName != null || isImported) return {
            KotlinMoveUsage.createIfPossible(it.mainReference, declarationNotNull, addImportToOriginalFile = false, isInternal = true)
        }

        return null
    }

    @Suppress("DEPRECATION")
    val bindingContext = analyzeWithAllCompilerChecks().bindingContext
    forEachDescendantOfType<KtReferenceExpression> { refExpr ->
        if (refExpr !is KtSimpleNameExpression || refExpr.parent is KtThisExpression) return@forEachDescendantOfType

        processReference(refExpr, bindingContext)?.let { body(refExpr, it) }
    }
}

private fun isCallableReference(reference: PsiReference): Boolean {
    return reference is KtSimpleNameReference
            && reference.element.getParentOfTypeAndBranch<KtCallableReferenceExpression> { callableReference } != null
}

class ImplicitCompanionAsDispatchReceiverUsageInfo(
    callee: KtSimpleNameExpression,
    val companionDescriptor: ClassDescriptor
) : UsageInfo(callee)

internal fun logFusForMoveRefactoring(
    numberOfEntities: Int,
    entity: KotlinMoveRefactoringFUSCollector.MovedEntity,
    destination: KotlinMoveRefactoringFUSCollector.MoveRefactoringDestination,
    isDefault: Boolean,
    body: Runnable
) {
    val timeStarted = currentTimeMillis()

    var succeeded = false
    try {
        body.run()
        succeeded = true
    } finally {
        KotlinMoveRefactoringFUSCollector.log(
            timeStarted = timeStarted,
            timeFinished = currentTimeMillis(),
            numberOfEntities = numberOfEntities,
            destination = destination,
            isDefault = isDefault,
            entity = entity,
            isSucceeded = succeeded,
        )
    }
}