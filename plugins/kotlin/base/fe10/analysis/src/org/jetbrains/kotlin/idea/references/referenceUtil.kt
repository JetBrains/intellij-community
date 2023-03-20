// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.references

import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiReference
import com.intellij.psi.search.GlobalSearchScope
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.kotlin.builtins.isExtensionFunctionType
import org.jetbrains.kotlin.descriptors.*
import org.jetbrains.kotlin.idea.caches.resolve.safeAnalyzeNonSourceRootCode
import org.jetbrains.kotlin.idea.imports.canBeReferencedViaImport
import org.jetbrains.kotlin.idea.imports.importableFqName
import org.jetbrains.kotlin.idea.stubindex.KotlinFullClassNameIndex
import org.jetbrains.kotlin.idea.stubindex.KotlinFunctionShortNameIndex
import org.jetbrains.kotlin.idea.stubindex.KotlinPropertyShortNameIndex
import org.jetbrains.kotlin.idea.stubindex.KotlinTypeAliasShortNameIndex
import org.jetbrains.kotlin.idea.util.CallTypeAndReceiver
import org.jetbrains.kotlin.kdoc.psi.impl.KDocName
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.resolve.BindingContext
import org.jetbrains.kotlin.resolve.FunctionImportedFromObject
import org.jetbrains.kotlin.resolve.PropertyImportedFromObject
import org.jetbrains.kotlin.resolve.calls.model.VariableAsFunctionResolvedCall
import org.jetbrains.kotlin.resolve.calls.util.getResolvedCall
import org.jetbrains.kotlin.resolve.descriptorUtil.isExtension
import org.jetbrains.kotlin.resolve.lazy.BodyResolveMode
import org.jetbrains.kotlin.resolve.source.getPsi
import org.jetbrains.kotlin.serialization.deserialization.descriptors.DeserializedClassDescriptor
import org.jetbrains.kotlin.serialization.deserialization.descriptors.DeserializedPropertyDescriptor
import org.jetbrains.kotlin.serialization.deserialization.descriptors.DeserializedSimpleFunctionDescriptor
import org.jetbrains.kotlin.serialization.deserialization.descriptors.DeserializedTypeAliasDescriptor

@Deprecated("For binary compatibility with AS, see KT-42061", replaceWith = ReplaceWith("mainReference"))
@get:JvmName("getMainReference")
val KtSimpleNameExpression.mainReferenceCompat: KtSimpleNameReference
    get() = mainReference

@Deprecated("For binary compatibility with AS, see KT-42061", replaceWith = ReplaceWith("mainReference"))
@get:JvmName("getMainReference")
val KtReferenceExpression.mainReferenceCompat: KtReference
    get() = mainReference

@get:ApiStatus.ScheduledForRemoval
@get:Deprecated("For binary compatibility with AS, see KT-42061", replaceWith = ReplaceWith("mainReference"))
@get:JvmName("getMainReference")
val KDocName.mainReferenceCompat: KDocReference
    get() = mainReference

@Deprecated("For binary compatibility with AS, see KT-42061", replaceWith = ReplaceWith("mainReference"))
@get:JvmName("getMainReference")
val KtElement.mainReferenceCompat: KtReference?
    get() = mainReference

fun DeclarationDescriptor.findPsiDeclarations(project: Project, resolveScope: GlobalSearchScope): Collection<PsiElement> {
    val fqName = importableFqName ?: return emptyList()

    fun Collection<KtNamedDeclaration>.fqNameFilter() = filter { it.fqName == fqName }
    return when (this) {
        is DeserializedClassDescriptor ->
            KotlinFullClassNameIndex.get(fqName.asString(), project, resolveScope)

        is DeserializedTypeAliasDescriptor ->
            KotlinTypeAliasShortNameIndex.get(fqName.shortName().asString(), project, resolveScope).fqNameFilter()

        is DeserializedSimpleFunctionDescriptor,
        is FunctionImportedFromObject ->
            KotlinFunctionShortNameIndex.get(fqName.shortName().asString(), project, resolveScope).fqNameFilter()

        is DeserializedPropertyDescriptor,
        is PropertyImportedFromObject ->
            KotlinPropertyShortNameIndex.get(fqName.shortName().asString(), project, resolveScope).fqNameFilter()

        is DeclarationDescriptorWithSource -> listOfNotNull(source.getPsi())

        else -> emptyList()
    }
}

fun KtElement.resolveMainReferenceToDescriptors(): Collection<DeclarationDescriptor> {
    val bindingContext = safeAnalyzeNonSourceRootCode(BodyResolveMode.PARTIAL)
    return mainReference?.resolveToDescriptors(bindingContext) ?: emptyList()
}

fun PsiReference.getImportAlias(): KtImportAlias? {
    return (this as? KtSimpleNameReference)?.getImportAlias()
}

// ----------- Read/write access -----------------------------------------------------------------------------------------------------------------------

fun KtReference.canBeResolvedViaImport(target: DeclarationDescriptor, bindingContext: BindingContext): Boolean {
    if (this is KDocReference) {
        val qualifier = element.getQualifier() ?: return true
        return if (target.isExtension) {
            val elementHasFunctionDescriptor = element.resolveMainReferenceToDescriptors().any { it is FunctionDescriptor }
            val qualifierHasClassDescriptor = qualifier.resolveMainReferenceToDescriptors().any { it is ClassDescriptor }
            elementHasFunctionDescriptor && qualifierHasClassDescriptor
        } else {
            false
        }
    }
    return element.canBeResolvedViaImport(target, bindingContext)
}

fun KtElement.canBeResolvedViaImport(target: DeclarationDescriptor, bindingContext: BindingContext): Boolean {
    if (!target.canBeReferencedViaImport()) return false
    if (target.isExtension) return true // assume that any type of reference can use imports when resolved to extension
    if (this !is KtNameReferenceExpression) return false

    val callTypeAndReceiver = CallTypeAndReceiver.detect(this)
    if (callTypeAndReceiver.receiver != null) {
        if (target !is PropertyDescriptor || !target.type.isExtensionFunctionType) return false
        if (callTypeAndReceiver !is CallTypeAndReceiver.DOT && callTypeAndReceiver !is CallTypeAndReceiver.SAFE) return false

        val resolvedCall = bindingContext[BindingContext.CALL, this].getResolvedCall(bindingContext)
                as? VariableAsFunctionResolvedCall ?: return false
        if (resolvedCall.variableCall.explicitReceiverKind.isDispatchReceiver) return false
    }

    if (parent is KtThisExpression || parent is KtSuperExpression) return false // TODO: it's a bad design of PSI tree, we should change it
    return true
}
