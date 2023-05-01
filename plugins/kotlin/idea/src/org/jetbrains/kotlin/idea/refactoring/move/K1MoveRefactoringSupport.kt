// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.refactoring.move

import com.intellij.psi.*
import com.intellij.psi.search.SearchScope
import org.jetbrains.kotlin.backend.jvm.ir.psiElement
import org.jetbrains.kotlin.descriptors.*
import org.jetbrains.kotlin.idea.base.searching.usages.KotlinFindUsagesHandlerFactory
import org.jetbrains.kotlin.idea.base.utils.fqname.isImported
import org.jetbrains.kotlin.idea.caches.resolve.analyze
import org.jetbrains.kotlin.idea.caches.resolve.analyzeWithAllCompilerChecks
import org.jetbrains.kotlin.idea.caches.resolve.analyzeWithContent
import org.jetbrains.kotlin.idea.caches.resolve.unsafeResolveToDescriptor
import org.jetbrains.kotlin.idea.codeInsight.DescriptorToSourceUtilsIde
import org.jetbrains.kotlin.idea.imports.importableFqName
import org.jetbrains.kotlin.idea.references.KtSimpleNameReference
import org.jetbrains.kotlin.idea.references.mainReference
import org.jetbrains.kotlin.load.java.descriptors.JavaCallableMemberDescriptor
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.containingClassOrObject
import org.jetbrains.kotlin.psi.psiUtil.forEachDescendantOfType
import org.jetbrains.kotlin.psi.psiUtil.getParentOfTypeAndBranch
import org.jetbrains.kotlin.resolve.BindingContext
import org.jetbrains.kotlin.resolve.DescriptorUtils
import org.jetbrains.kotlin.resolve.calls.model.VariableAsFunctionResolvedCall
import org.jetbrains.kotlin.resolve.calls.util.getResolvedCall
import org.jetbrains.kotlin.resolve.descriptorUtil.*
import org.jetbrains.kotlin.resolve.lazy.BodyResolveMode
import org.jetbrains.kotlin.resolve.scopes.receivers.ImplicitClassReceiver
import org.jetbrains.kotlin.resolve.scopes.receivers.ImplicitReceiver
import org.jetbrains.kotlin.types.expressions.DoubleColonLHS

internal class K1MoveRefactoringSupport : KotlinMoveRefactoringSupport {
    override fun findReferencesToHighlight(target: PsiElement, searchScope: SearchScope): Collection<PsiReference> {
        return KotlinFindUsagesHandlerFactory(target.project).createFindUsagesHandler(target, false)
            .findReferencesToHighlight(target, searchScope)
    }

    override fun isExtensionRef(expr: KtSimpleNameExpression): Boolean {
        val resolvedCall = expr.getResolvedCall(expr.analyze(BodyResolveMode.PARTIAL)) ?: return false
        if (resolvedCall is VariableAsFunctionResolvedCall) {
            return resolvedCall.variableCall.candidateDescriptor.isExtension || resolvedCall.functionCall.candidateDescriptor.isExtension
        }
        return resolvedCall.candidateDescriptor.isExtension
    }

    override fun isQualifiable(callableReferenceExpression: KtCallableReferenceExpression): Boolean {
        val receiverExpression = callableReferenceExpression.receiverExpression
        val lhs = callableReferenceExpression.analyze(BodyResolveMode.PARTIAL)[BindingContext.DOUBLE_COLON_LHS, receiverExpression]
        return lhs is DoubleColonLHS.Type
    }

    override fun traverseOuterInstanceReferences(
        member: KtNamedDeclaration,
        stopAtFirst: Boolean,
        body: (OuterInstanceReferenceUsageInfo) -> Unit
    ): Boolean {
        if (member is KtObjectDeclaration || member is KtClass && !member.isInner()) return false
        val context = member.analyzeWithContent()
        val containingClassOrObject = member.containingClassOrObject ?: return false
        val outerClassDescriptor = containingClassOrObject.unsafeResolveToDescriptor() as ClassDescriptor
        var found = false
        member.accept(object : PsiRecursiveElementWalkingVisitor() {
            private fun getOuterInstanceReference(element: PsiElement): OuterInstanceReferenceUsageInfo? {
                return when (element) {
                    is KtThisExpression -> {
                        val descriptor = context[BindingContext.REFERENCE_TARGET, element.instanceReference]
                        val isIndirect = when {
                            descriptor == outerClassDescriptor -> false
                            descriptor?.isAncestorOf(outerClassDescriptor, true) ?: false -> true
                            else -> return null
                        }
                        OuterInstanceReferenceUsageInfo.ExplicitThis(element, isIndirect)
                    }

                    is KtSimpleNameExpression -> {
                        val resolvedCall = element.getResolvedCall(context) ?: return null
                        val dispatchReceiver = resolvedCall.dispatchReceiver as? ImplicitReceiver
                        val extensionReceiver = resolvedCall.extensionReceiver as? ImplicitReceiver
                        var isIndirect = false
                        val isDoubleReceiver = when (outerClassDescriptor) {
                            dispatchReceiver?.declarationDescriptor -> extensionReceiver != null
                            extensionReceiver?.declarationDescriptor -> dispatchReceiver != null
                            else -> {
                                isIndirect = true
                                when {
                                    dispatchReceiver?.declarationDescriptor?.isAncestorOf(outerClassDescriptor, true) ?: false ->
                                        extensionReceiver != null

                                    extensionReceiver?.declarationDescriptor?.isAncestorOf(outerClassDescriptor, true) ?: false ->
                                        dispatchReceiver != null

                                    else -> return null
                                }
                            }
                        }
                        OuterInstanceReferenceUsageInfo.ImplicitReceiver(resolvedCall.call.callElement, isIndirect, isDoubleReceiver)
                    }

                    else -> null
                }
            }

            override fun visitElement(element: PsiElement) {
                getOuterInstanceReference(element)?.let {
                    body(it)
                    found = true
                    if (stopAtFirst) stopWalking()
                    return
                }
                super.visitElement(element)
            }
        })
        return found
    }

    override fun addDelayedImportRequest(elementToImport: PsiElement, file: KtFile) {
        org.jetbrains.kotlin.idea.codeInsight.shorten.addDelayedImportRequest(elementToImport, file)
    }

    override fun processInternalReferencesToUpdateOnPackageNameChange(
        element: KtElement,
        containerChangeInfo: MoveContainerChangeInfo,
        body: (originalRefExpr: KtSimpleNameExpression, usageFactory: KotlinUsageInfoFactory) -> Unit
    ) {
        val file = element.containingFile as? KtFile ?: return

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

        fun isCallableReference(reference: PsiReference): Boolean {
            return reference is KtSimpleNameReference && reference.element.getParentOfTypeAndBranch<KtCallableReferenceExpression> {
                callableReference
            } != null
        }

        fun processReference(refExpr: KtSimpleNameExpression, bindingContext: BindingContext): KotlinUsageInfoFactory? {
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
                var result = DescriptorToSourceUtilsIde.getAnyDeclaration(element.project, descriptor) ?: return@lazy null

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
                        val psiClass = implicitClass?.psiElement
                        if (psiClass is KtObjectDeclaration && psiClass.isCompanion()) {
                            return { ImplicitCompanionAsDispatchReceiverUsageInfo(it, psiClass) }
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
            if (isImported && element is KtFile) return null

            val declarationNotNull = declaration ?: return null

            if (isExtension || containerFqName != null || isImported) return {
                KotlinMoveRenameUsage.createIfPossible(it.mainReference, declarationNotNull, addImportToOriginalFile = false, isInternal = true)
            }

            return null
        }

        @Suppress("DEPRECATION")
        val bindingContext = element.analyzeWithAllCompilerChecks().bindingContext
        element.forEachDescendantOfType<KtReferenceExpression> { refExpr ->
            if (refExpr !is KtSimpleNameExpression || refExpr.parent is KtThisExpression) return@forEachDescendantOfType

            processReference(refExpr, bindingContext)?.let { body(refExpr, it) }
        }
    }
}