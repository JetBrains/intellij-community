// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.search.usagesSearch

import com.intellij.openapi.project.Project
import com.intellij.psi.*
import com.intellij.psi.util.MethodSignatureUtil
import org.jetbrains.kotlin.asJava.classes.lazyPub
import org.jetbrains.kotlin.asJava.toLightClass
import org.jetbrains.kotlin.asJava.toLightMethods
import org.jetbrains.kotlin.descriptors.*
import org.jetbrains.kotlin.idea.base.facet.platform.platform
import org.jetbrains.kotlin.idea.base.projectStructure.IDELanguageSettingsProvider
import org.jetbrains.kotlin.idea.base.projectStructure.compositeAnalysis.findAnalyzerServices
import org.jetbrains.kotlin.idea.base.projectStructure.moduleInfoOrNull
import org.jetbrains.kotlin.idea.caches.resolve.analyze
import org.jetbrains.kotlin.idea.caches.resolve.getResolutionFacade
import org.jetbrains.kotlin.idea.caches.resolve.resolveToDescriptorIfAny
import org.jetbrains.kotlin.idea.caches.resolve.resolveToParameterDescriptorIfAny
import org.jetbrains.kotlin.idea.caches.resolve.util.getJavaMemberDescriptor
import org.jetbrains.kotlin.idea.caches.resolve.util.getJavaMethodDescriptor
import org.jetbrains.kotlin.idea.caches.resolve.util.getJavaOrKotlinMemberDescriptor
import org.jetbrains.kotlin.idea.caches.resolve.util.hasJavaResolutionFacade
import org.jetbrains.kotlin.idea.codeInsight.DescriptorToSourceUtilsIde
import org.jetbrains.kotlin.idea.core.compareDescriptors
import org.jetbrains.kotlin.idea.references.unwrappedTargets
import org.jetbrains.kotlin.idea.search.KotlinSearchUsagesSupport
import org.jetbrains.kotlin.idea.search.ReceiverTypeSearcherInfo
import org.jetbrains.kotlin.idea.util.FuzzyType
import com.intellij.openapi.application.runReadAction
import org.jetbrains.kotlin.idea.util.fuzzyExtensionReceiverType
import org.jetbrains.kotlin.idea.util.toFuzzyType
import org.jetbrains.kotlin.load.java.descriptors.JavaClassDescriptor
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.getNonStrictParentOfType
import org.jetbrains.kotlin.renderer.DescriptorRenderer
import org.jetbrains.kotlin.resolve.*
import org.jetbrains.kotlin.resolve.descriptorUtil.isExtension
import org.jetbrains.kotlin.resolve.descriptorUtil.isTypeRefinementEnabled
import org.jetbrains.kotlin.resolve.descriptorUtil.module
import org.jetbrains.kotlin.resolve.lazy.BodyResolveMode
import org.jetbrains.kotlin.resolve.sam.getSingleAbstractMethodOrNull
import org.jetbrains.kotlin.scripting.definitions.findScriptDefinition
import org.jetbrains.kotlin.types.KotlinType
import org.jetbrains.kotlin.util.isValidOperator

@Deprecated(
    "This method is obsolete and will be removed",
    ReplaceWith(
        "resolveToDescriptorIfAny(BodyResolveMode.FULL)",
        "org.jetbrains.kotlin.idea.caches.resolve.resolveToDescriptorIfAny",
        "org.jetbrains.kotlin.resolve.lazy.BodyResolveMode"
    )
)
@get:JvmName("getDescriptor")
val KtDeclaration.descriptorCompat: DeclarationDescriptor?
    get() = if (this is KtParameter) this.descriptorCompat else this.resolveToDescriptorIfAny(BodyResolveMode.FULL)

@Deprecated(
    "This method is obsolete and will be removed",
    ReplaceWith(
        "resolveToParameterDescriptorIfAny(BodyResolveMode.FULL)",
        "org.jetbrains.kotlin.idea.caches.resolve.resolveToParameterDescriptorIfAny",
        "org.jetbrains.kotlin.resolve.lazy.BodyResolveMode"
    )
)

@get:JvmName("getDescriptor")
val KtParameter.descriptorCompat: ValueParameterDescriptor?
    get() = this.resolveToParameterDescriptorIfAny(BodyResolveMode.FULL)

class KotlinConstructorCallLazyDescriptorHandle(ktElement: KtDeclaration) :
    KotlinSearchUsagesSupport.ConstructorCallHandle {
    private val descriptor: ConstructorDescriptor? by lazyPub { ktElement.constructor }

    override fun referencedTo(element: KtElement): Boolean =
        element.getConstructorCallDescriptor().let {
            it != null && descriptor != null && compareDescriptors(element.project, it, descriptor)
        }
}

class JavaConstructorCallLazyDescriptorHandle(psiMethod: PsiMethod) :
    KotlinSearchUsagesSupport.ConstructorCallHandle {

    private val descriptor: ConstructorDescriptor? by lazyPub { psiMethod.getJavaMethodDescriptor() as? ConstructorDescriptor }

    override fun referencedTo(element: KtElement): Boolean =
        element.getConstructorCallDescriptor().let {
            it != null && descriptor != null && compareDescriptors(element.project, it, descriptor)
        }
}

fun tryRenderDeclarationCompactStyle(declaration: KtDeclaration): String? =
    declaration.descriptor?.let { DescriptorRenderer.COMPACT.render(it) }

fun isSamInterface(psiClass: PsiClass): Boolean {
    val classDescriptor = psiClass.getJavaMemberDescriptor() as? JavaClassDescriptor
    return classDescriptor != null && getSingleAbstractMethodOrNull(classDescriptor) != null
}

fun hasType(element: KtExpression): Boolean =
    element.analyze(BodyResolveMode.PARTIAL).getType(element) != null

val KtDeclaration.constructor: ConstructorDescriptor?
    get() {
        val context = this.analyze()
        return when (this) {
            is KtClassOrObject -> context[BindingContext.CLASS, this]?.unsubstitutedPrimaryConstructor
            is KtFunction -> context[BindingContext.CONSTRUCTOR, this]
            else -> null
        }
    }

val KtParameter.propertyDescriptor: PropertyDescriptor?
    get() = this.resolveToDescriptorIfAny(BodyResolveMode.FULL) as? PropertyDescriptor

fun PsiReference.checkUsageVsOriginalDescriptor(
    targetDescriptor: DeclarationDescriptor,
    declarationToDescriptor: (KtDeclaration) -> DeclarationDescriptor? = { it.descriptor },
    checker: (usageDescriptor: DeclarationDescriptor, targetDescriptor: DeclarationDescriptor) -> Boolean
): Boolean {
    return unwrappedTargets
        .filterIsInstance<KtDeclaration>()
        .any {
            val usageDescriptor = declarationToDescriptor(it)
            usageDescriptor != null && checker(usageDescriptor, targetDescriptor)
        }
}

fun PsiReference.isKotlinConstructorUsage(ktClassOrObject: KtClassOrObject): Boolean = with(element) {
    if (this !is KtElement) return false

    val descriptor = getConstructorCallDescriptor() as? ConstructorDescriptor ?: return false

    val declaration = DescriptorToSourceUtils.descriptorToDeclaration(descriptor.containingDeclaration)
    return declaration == ktClassOrObject || (declaration is KtConstructor<*> && declaration.getContainingClassOrObject() == ktClassOrObject)
}

private fun KtElement.getConstructorCallDescriptor(): DeclarationDescriptor? {
    val bindingContext = this.analyze()
    val constructorCalleeExpression = getNonStrictParentOfType<KtConstructorCalleeExpression>()
    if (constructorCalleeExpression != null) {
        return bindingContext.get(BindingContext.REFERENCE_TARGET, constructorCalleeExpression.constructorReferenceExpression)
    }

    val callExpression = getNonStrictParentOfType<KtCallElement>()
    if (callExpression != null) {
        val callee = callExpression.calleeExpression
        if (callee is KtReferenceExpression) {
            return bindingContext.get(BindingContext.REFERENCE_TARGET, callee)
        }
    }

    return null
}

// Check if reference resolves to extension function whose receiver is the same as declaration's parent (or its superclass)
// Used in extension search
fun PsiReference.isExtensionOfDeclarationClassUsage(declaration: KtNamedDeclaration): Boolean {
    val descriptor = declaration.descriptor ?: return false
    return checkUsageVsOriginalDescriptor(descriptor) { usageDescriptor, targetDescriptor ->
        when (usageDescriptor) {
            targetDescriptor -> false
            !is FunctionDescriptor -> false
            else -> {
                val receiverDescriptor =
                    usageDescriptor.extensionReceiverParameter?.type?.constructor?.declarationDescriptor
                val containingDescriptor = targetDescriptor.containingDeclaration

                containingDescriptor == receiverDescriptor
                        || (containingDescriptor is ClassDescriptor
                        && receiverDescriptor is ClassDescriptor
                        && DescriptorUtils.isSubclass(containingDescriptor, receiverDescriptor))
            }
        }
    }
}

// Check if reference resolves to the declaration with the same parent
// Used in overload search
fun PsiReference.isUsageInContainingDeclaration(declaration: KtNamedDeclaration): Boolean {
    val descriptor = declaration.descriptor ?: return false
    return checkUsageVsOriginalDescriptor(descriptor) { usageDescriptor, targetDescriptor ->
        usageDescriptor != targetDescriptor
                && usageDescriptor.containingDeclaration == targetDescriptor.containingDeclaration
    }
}

fun PsiReference.isCallableOverrideUsage(declaration: KtNamedDeclaration): Boolean {
    val toDescriptor: (KtDeclaration) -> CallableDescriptor? = { sourceDeclaration ->
        if (sourceDeclaration is KtParameter) {
            // we don't treat parameters in overriding method as "override" here (overriding parameters usages are searched optionally and via searching of overriding methods first)
            if (sourceDeclaration.hasValOrVar()) sourceDeclaration.propertyDescriptor else null
        } else {
            sourceDeclaration.descriptor as? CallableDescriptor
        }
    }

    val targetDescriptor = toDescriptor(declaration) ?: return false

    return unwrappedTargets.any {
        when (it) {
            is KtDeclaration -> {
                val usageDescriptor = toDescriptor(it)
                usageDescriptor != null && OverridingUtil.overrides(
                    usageDescriptor,
                    targetDescriptor,
                    usageDescriptor.module.isTypeRefinementEnabled(),
                    false // don't distinguish between expect and non-expect callable descriptors, KT-38298, KT-38589
                )
            }
            is PsiMethod -> {
                declaration.toLightMethods().any { superMethod -> MethodSignatureUtil.isSuperMethod(superMethod, it) }
            }
            else -> false
        }
    }
}

fun KtFile.forceResolveReferences(elements: List<KtElement>) {
    getResolutionFacade().analyze(elements, BodyResolveMode.PARTIAL)
}

private fun PsiElement.resolveTargetToDescriptor(isDestructionDeclarationSearch: Boolean): FunctionDescriptor? {

    if (isDestructionDeclarationSearch && this is KtParameter) {
        return dataClassComponentFunction()
    }

    return when {
        this is KtDeclaration -> resolveToDescriptorIfAny(BodyResolveMode.FULL)
        this is PsiMember && hasJavaResolutionFacade() ->
            this.getJavaOrKotlinMemberDescriptor()
        else -> null
    } as? FunctionDescriptor
}

private fun containsTypeOrDerivedInside(declaration: KtDeclaration, typeToSearch: FuzzyType): Boolean {

    fun KotlinType.containsTypeOrDerivedInside(): Boolean {
        return typeToSearch.checkIsSuperTypeOf(this) != null || arguments.any { !it.isStarProjection && it.type.containsTypeOrDerivedInside() }
    }

    val descriptor = declaration.resolveToDescriptorIfAny() as? CallableDescriptor
    val type = descriptor?.returnType
    return type != null && type.containsTypeOrDerivedInside()
}

private fun FuzzyType.toPsiClass(project: Project): PsiClass? {
    val classDescriptor = type.constructor.declarationDescriptor ?: return null
    return when (val classDeclaration = DescriptorToSourceUtilsIde.getAnyDeclaration(project, classDescriptor)) {
        is PsiClass -> classDeclaration
        is KtClassOrObject -> classDeclaration.toLightClass()
        else -> null
    }
}

private fun PsiElement.extractReceiverType(isDestructionDeclarationSearch: Boolean): FuzzyType? {
    val descriptor = resolveTargetToDescriptor(isDestructionDeclarationSearch)?.takeIf { it.isValidOperator() } ?: return null

    return if (descriptor.isExtension) {
        descriptor.fuzzyExtensionReceiverType()!!
    } else {
        val classDescriptor = descriptor.containingDeclaration as? ClassDescriptor ?: return null
        classDescriptor.defaultType.toFuzzyType(classDescriptor.typeConstructor.parameters)
    }
}

fun PsiElement.getReceiverTypeSearcherInfo(isDestructionDeclarationSearch: Boolean): ReceiverTypeSearcherInfo? {
    val receiverType = runReadAction { extractReceiverType(isDestructionDeclarationSearch) } ?: return null
    val psiClass = runReadAction { receiverType.toPsiClass(project) }
    return ReceiverTypeSearcherInfo(psiClass) {
        containsTypeOrDerivedInside(it, receiverType)
    }
}

fun KtFile.getDefaultImports(): List<ImportPath> {
    val moduleInfo = this.moduleInfoOrNull ?: return emptyList()
    return platform
        .findAnalyzerServices(project)
        .getDefaultImports(IDELanguageSettingsProvider.getLanguageVersionSettings(moduleInfo, project), includeLowPriorityImports = true)
}

fun PsiFile.scriptDefinitionExists(): Boolean = findScriptDefinition() != null
