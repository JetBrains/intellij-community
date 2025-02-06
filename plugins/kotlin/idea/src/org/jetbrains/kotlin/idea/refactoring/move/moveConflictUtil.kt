// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.refactoring.move

import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleUtilCore
import com.intellij.openapi.module.impl.scopes.JdkScope
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.JdkOrderEntry
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.*
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.SearchScope
import com.intellij.psi.search.searches.ClassInheritorsSearch
import com.intellij.psi.search.searches.ClassInheritorsSearch.SearchParameters
import com.intellij.psi.search.searches.ReferencesSearch
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.refactoring.RefactoringBundle
import com.intellij.refactoring.util.CommonRefactoringUtil
import com.intellij.refactoring.util.MoveRenameUsageInfo
import com.intellij.refactoring.util.NonCodeUsageInfo
import com.intellij.refactoring.util.RefactoringUIUtil
import com.intellij.usageView.UsageInfo
import com.intellij.usageView.UsageViewTypeLocation
import com.intellij.util.containers.MultiMap
import org.jetbrains.kotlin.asJava.namedUnwrappedElement
import org.jetbrains.kotlin.asJava.toLightClass
import org.jetbrains.kotlin.asJava.toLightMethods
import org.jetbrains.kotlin.builtins.KotlinBuiltIns
import org.jetbrains.kotlin.config.LanguageFeature
import org.jetbrains.kotlin.config.LanguageVersionSettings
import org.jetbrains.kotlin.config.TestSourceKotlinRootType
import org.jetbrains.kotlin.descriptors.*
import org.jetbrains.kotlin.descriptors.impl.MutablePackageFragmentDescriptor
import org.jetbrains.kotlin.idea.base.facet.implementedModules
import org.jetbrains.kotlin.idea.base.facet.platform.platform
import org.jetbrains.kotlin.idea.base.platforms.forcedTargetPlatform
import org.jetbrains.kotlin.idea.base.projectStructure.*
import org.jetbrains.kotlin.idea.base.psi.kotlinFqName
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.base.util.and
import org.jetbrains.kotlin.idea.base.util.module
import org.jetbrains.kotlin.idea.base.util.not
import org.jetbrains.kotlin.idea.caches.resolve.*
import org.jetbrains.kotlin.idea.caches.resolve.util.getJavaMemberDescriptor
import org.jetbrains.kotlin.idea.caches.resolve.util.hasJavaResolutionFacade
import org.jetbrains.kotlin.idea.caches.resolve.util.javaResolutionFacade
import org.jetbrains.kotlin.idea.caches.resolve.util.resolveToDescriptor
import org.jetbrains.kotlin.idea.codeInsight.DescriptorToSourceUtilsIde
import org.jetbrains.kotlin.idea.core.getPackage
import org.jetbrains.kotlin.idea.core.util.toPsiDirectory
import org.jetbrains.kotlin.idea.imports.importableFqName
import org.jetbrains.kotlin.idea.refactoring.getUsageContext
import org.jetbrains.kotlin.idea.refactoring.pullUp.renderForConflicts
import org.jetbrains.kotlin.idea.resolve.languageVersionSettings
import org.jetbrains.kotlin.idea.search.usagesSearch.descriptor
import org.jetbrains.kotlin.js.resolve.diagnostics.findPsi
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.load.java.JavaDescriptorVisibilities
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.platform.jvm.isJvm
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.*
import org.jetbrains.kotlin.renderer.ClassifierNamePolicy
import org.jetbrains.kotlin.renderer.DescriptorRenderer
import org.jetbrains.kotlin.renderer.ParameterNameRenderingPolicy
import org.jetbrains.kotlin.resolve.BindingContext
import org.jetbrains.kotlin.resolve.DescriptorUtils
import org.jetbrains.kotlin.resolve.descriptorUtil.*
import org.jetbrains.kotlin.resolve.jvm.KotlinJavaPsiFacade
import org.jetbrains.kotlin.resolve.lazy.BodyResolveMode
import org.jetbrains.kotlin.resolve.lazy.descriptors.findPackageFragmentForFile
import org.jetbrains.kotlin.resolve.source.KotlinSourceElement
import org.jetbrains.kotlin.resolve.source.getPsi
import org.jetbrains.kotlin.types.KotlinType
import org.jetbrains.kotlin.types.typeUtil.isAnyOrNullableAny
import org.jetbrains.kotlin.util.isJavaDescriptor
import org.jetbrains.kotlin.util.supertypesWithAny
import org.jetbrains.kotlin.utils.SmartSet
import org.jetbrains.kotlin.utils.addToStdlib.firstIsInstanceOrNull
import java.util.*

fun analyzeConflictsInFile(
    file: KtFile,
    usages: Collection<UsageInfo>,
    moveTarget: KotlinMoveTarget,
    allElementsToMove: Collection<PsiElement>,
    onUsageUpdate: (List<UsageInfo>) -> Unit
): MultiMap<PsiElement, String> {
    val elementsToMove = file.declarations
    if (elementsToMove.isEmpty()) return MultiMap.empty()

    val (internalUsages, externalUsages) = usages.partition { it is KotlinMoveRenameUsage && it.isInternal }
    val internalUsageSet = internalUsages.toMutableSet()
    val externalUsageSet = externalUsages.toMutableSet()

    val moveCheckerInfo = KotlinMoveConflictCheckerInfo(
        file.project,
        elementsToMove,
        moveTarget,
        elementsToMove.first(),
        allElementsToMove
    )
    val conflicts = checkAllConflicts(moveCheckerInfo, internalUsageSet, externalUsageSet)

    if (externalUsageSet.size != externalUsages.size || internalUsageSet.size != internalUsages.size) {
        onUsageUpdate((externalUsageSet + internalUsageSet).toList())
    }
    return conflicts
}

class KotlinMoveConflictCheckerInfo(
    val project: Project,
    val elementsToMove: Collection<KtElement>,
    val moveTarget: KotlinMoveTarget,
    val context: KtElement,
    allElementsToMove: Collection<PsiElement>? = null
) {
    val allElementsToMove = allElementsToMove ?: elementsToMove

    fun isToBeMoved(element: PsiElement): Boolean = allElementsToMove.any { it.isAncestor(element, false) }
}

fun checkAllConflicts(
    moveCheckerInfo: KotlinMoveConflictCheckerInfo,
    internalUsages: MutableSet<UsageInfo>,
    externalUsages: MutableSet<UsageInfo>
) = MultiMap<PsiElement, String>().apply {
    putAllValues(checkNameClashes(moveCheckerInfo))
    putAllValues(checkVisibilityInDeclarations(moveCheckerInfo))
    putAllValues(checkInternalMemberUsages(moveCheckerInfo))
    putAllValues(checkSealedClassMove(moveCheckerInfo))
    putAllValues(checkModuleConflictsInDeclarations(moveCheckerInfo, internalUsages))
    putAllValues(checkModuleConflictsInUsages(moveCheckerInfo, externalUsages))
    putAllValues(checkVisibilityInUsages(moveCheckerInfo, externalUsages))
}

private val DESCRIPTOR_RENDERER_FOR_COMPARISON = DescriptorRenderer.withOptions {
    withDefinedIn = true
    classifierNamePolicy = ClassifierNamePolicy.FULLY_QUALIFIED
    modifiers = emptySet()
    withoutTypeParameters = true
    parameterNameRenderingPolicy = ParameterNameRenderingPolicy.NONE
    includeAdditionalModifiers = false
    renderUnabbreviatedType = false
    withoutSuperTypes = true
}

private fun PackageFragmentDescriptor.withSource(sourceFile: KtFile): PackageFragmentDescriptor {
    return object : PackageFragmentDescriptor by this {
        override fun getOriginal() = this
        override fun getSource() = KotlinSourceElement(sourceFile)
    }
}

private fun VirtualFile.getModuleDescriptor(context: KtElement): ModuleDescriptor? {
    val moduleInfo = ModuleInfoProvider.getInstance(context.project).firstOrNull(this) ?: return null
    return context.getResolutionFacade().findModuleDescriptor(moduleInfo)
}

private fun KotlinMoveTarget.getContainerDescriptor(context: KtElement): DeclarationDescriptor? {
    return when (this) {
        is KotlinMoveTarget.ExistingElement -> when (val targetElement = targetElement) {
            is KtNamedDeclaration -> context.getResolutionFacade().resolveToDescriptor(targetElement)
            is KtFile -> {
                val packageFragment = targetElement.findModuleDescriptor().findPackageFragmentForFile(targetElement)
                packageFragment?.withSource(targetElement)
            }
            else -> null
        }

        is KotlinMoveTarget.Directory, is KotlinMoveTarget.DeferredFile -> {
            val packageFqName = targetContainerFqName ?: return null
            val targetModuleDescriptor = targetFileOrDir?.let { it.getModuleDescriptor(context) ?: return null }
                ?: context.getResolutionFacade().moduleDescriptor
            val fakeFile = KtPsiFactory(context.project).createFile("")
            MutablePackageFragmentDescriptor(targetModuleDescriptor, packageFqName).withSource(fakeFile)

        }
        else -> null
    }
}

private fun DeclarationDescriptor.isVisibleIn(where: DeclarationDescriptor, languageVersionSettings: LanguageVersionSettings): Boolean {
    return when {
        this !is DeclarationDescriptorWithVisibility -> true
        !DescriptorVisibilityUtils.isVisibleIgnoringReceiver(this, where, languageVersionSettings) -> false
        this is ConstructorDescriptor -> DescriptorVisibilityUtils.isVisibleIgnoringReceiver(containingDeclaration, where, languageVersionSettings)
        else -> true
    }
}

private fun DeclarationDescriptor.wrap(
    newContainer: DeclarationDescriptor? = null,
    newVisibility: DescriptorVisibility? = null
): DeclarationDescriptor? {
    if (newContainer == null && newVisibility == null) return this

    return when (val wrappedDescriptor = this) {
        // We rely on visibility not depending on more specific type of CallableMemberDescriptor
        is CallableMemberDescriptor -> object : CallableMemberDescriptor by wrappedDescriptor {
            override fun getOriginal() = this
            override fun getContainingDeclaration() = newContainer ?: wrappedDescriptor.containingDeclaration
            override fun getVisibility(): DescriptorVisibility = newVisibility ?: wrappedDescriptor.visibility
            override fun getSource() =
                newContainer?.let { SourceElement { DescriptorUtils.getContainingSourceFile(it) } } ?: wrappedDescriptor.source
        }
        is ClassDescriptor -> object : ClassDescriptor by wrappedDescriptor {
            override fun getOriginal() = this
            override fun getContainingDeclaration() = newContainer ?: wrappedDescriptor.containingDeclaration
            override fun getVisibility(): DescriptorVisibility = newVisibility ?: wrappedDescriptor.visibility
            override fun getSource() =
                newContainer?.let { SourceElement { DescriptorUtils.getContainingSourceFile(it) } } ?: wrappedDescriptor.source
        }
        else -> null
    }
}

private fun DeclarationDescriptor.asPredicted(
    newContainer: DeclarationDescriptor,
    actualVisibility: DescriptorVisibility?
): DeclarationDescriptor? {
    val visibility = actualVisibility ?: (this as? DeclarationDescriptorWithVisibility)?.visibility ?: return null
    val adjustedVisibility = if (visibility == DescriptorVisibilities.PROTECTED && newContainer is PackageFragmentDescriptor) {
        DescriptorVisibilities.PUBLIC
    } else {
        visibility
    }
    return wrap(newContainer, adjustedVisibility)
}

private fun DeclarationDescriptor.visibilityAsViewedFromJava(): DescriptorVisibility? {
    if (this !is DeclarationDescriptorWithVisibility) return null
    return when (visibility) {
        DescriptorVisibilities.PRIVATE -> {
            if (this is ClassDescriptor && DescriptorUtils.isTopLevelDeclaration(this)) JavaDescriptorVisibilities.PACKAGE_VISIBILITY else null
        }
        DescriptorVisibilities.PROTECTED -> JavaDescriptorVisibilities.PROTECTED_AND_PACKAGE
        else -> null
    }
}

private fun render(declaration: PsiElement) = RefactoringUIUtil.getDescription(declaration, false)

private fun render(descriptor: DeclarationDescriptor) = CommonRefactoringUtil.htmlEmphasize(descriptor.renderForConflicts())

// Based on RefactoringConflictsUtil.analyzeModuleConflicts
private fun analyzeModuleConflictsInUsages(
    moveChecker: KotlinMoveConflictCheckerInfo,
    usages: Collection<UsageInfo>,
    targetScope: VirtualFile
): MultiMap<PsiElement, String> {
    val conflicts = MultiMap<PsiElement, String>()
    val project = moveChecker.project
    val targetModule = ModuleUtilCore.findModuleForFile(targetScope, project) ?: return MultiMap.empty()
    val projectFileIndex = ProjectFileIndex.getInstance(project)
    val isInTestSources = projectFileIndex.getKotlinSourceRootType(targetScope) == TestSourceKotlinRootType
    NextUsage@ for (usage in usages) {
        val element = usage.element ?: continue
        if (PsiTreeUtil.getParentOfType(element, PsiImportStatement::class.java, false) != null) continue
        if (moveChecker.isToBeMoved(element)) continue@NextUsage
        val resolveScope = element.resolveScope
        if (resolveScope.isSearchInModuleContent(targetModule, isInTestSources)) continue
        val usageModule = element.module ?: continue
        val scopeDescription = RefactoringUIUtil.getDescription(element.getUsageContext(), true)
        val referencedElement = (if (usage is MoveRenameUsageInfo) usage.referencedElement else usage.element) ?: error(usage)
        val message = if (usageModule == targetModule && isInTestSources) {
            RefactoringBundle.message(
                "0.referenced.in.1.will.not.be.accessible.from.production.of.module.2",
                RefactoringUIUtil.getDescription(referencedElement, true),
                scopeDescription,
                CommonRefactoringUtil.htmlEmphasize(usageModule.name)
            )
        } else {
            RefactoringBundle.message(
                "0.referenced.in.1.will.not.be.accessible.from.module.2",
                RefactoringUIUtil.getDescription(referencedElement, true),
                scopeDescription,
                CommonRefactoringUtil.htmlEmphasize(usageModule.name)
            )
        }
        conflicts.putValue(referencedElement, StringUtil.capitalize(message))
    }
    return conflicts
}

fun checkModuleConflictsInUsages(
    moveCheckerInfo: KotlinMoveConflictCheckerInfo,
    externalUsages: MutableSet<UsageInfo>
): MultiMap<PsiElement, String> {
    val conflicts = MultiMap<PsiElement, String>()
    val targetScope = moveCheckerInfo.moveTarget.targetFileOrDir ?: return MultiMap.empty()
    val newConflicts = analyzeModuleConflictsInUsages(moveCheckerInfo, externalUsages, targetScope)
    if (!newConflicts.isEmpty) {
        val referencedElementsToSkip = newConflicts.keySet().mapNotNullTo(HashSet()) { it.namedUnwrappedElement }
        externalUsages.removeIf { it is MoveRenameUsageInfo
                && it.referencedElement?.namedUnwrappedElement?.let { element -> element in referencedElementsToSkip } ?: false
        }
        conflicts.putAllValues(newConflicts)
    }
    return conflicts
}

private fun getScopeWithPlatformAwareDependencies(module: Module): SearchScope {
    val baseScope = GlobalSearchScope.moduleWithDependenciesAndLibrariesScope(module)
    if (module.platform.isJvm()) return baseScope
    return ModuleRootManager.getInstance(module)
        .orderEntries
        .filterIsInstance<JdkOrderEntry>()
        .fold(baseScope as SearchScope) { scope, jdkEntry -> scope and !JdkScope(module.project, jdkEntry) }
}

@OptIn(ExperimentalMultiplatform::class)
fun checkModuleConflictsInDeclarations(
    moveCheckerInfo: KotlinMoveConflictCheckerInfo,
    internalUsages: MutableSet<UsageInfo>,
): MultiMap<PsiElement, String> {
    val conflicts = MultiMap<PsiElement, String>()
    val targetScope = moveCheckerInfo.moveTarget.targetFileOrDir ?: return MultiMap.empty()
    val targetModule = ModuleUtilCore.findModuleForFile(targetScope, moveCheckerInfo.project) ?: return MultiMap.empty()
    val resolveScope = getScopeWithPlatformAwareDependencies(targetModule)
    fun isInScope(targetElement: PsiElement, targetDescriptor: DeclarationDescriptor): Boolean {
        if (targetElement in resolveScope) return true
        if (targetElement.manager.isInProject(targetElement)) return false
        val fqName = targetDescriptor.importableFqName ?: return true
        val importableDescriptor = targetDescriptor.getImportableDescriptor()
        val dummyFile = KtPsiFactory(targetElement.project).createFile("dummy.kt", "").apply {
            forcedModuleInfo = ModuleInfoProvider.getInstance(project).firstOrNull(targetScope)
            forcedTargetPlatform = targetModule.platform
        }
        val newTargetDescriptors = dummyFile.resolveImportReference(fqName)
        if (newTargetDescriptors.any { descriptor -> descriptor is MemberDescriptor && descriptor.isExpect
                    && descriptor.annotations.any { OptionalExpectation::class.qualifiedName!! == it.fqName?.asString() }
            }) return false
        if (importableDescriptor is TypeAliasDescriptor && newTargetDescriptors.any { it is ClassDescriptor
                    && it.isExpect && it.importableFqName == importableDescriptor.importableFqName
            }) return true
        val renderedImportableTarget = DESCRIPTOR_RENDERER_FOR_COMPARISON.render(importableDescriptor)
        val renderedTarget by lazy { DESCRIPTOR_RENDERER_FOR_COMPARISON.render(targetDescriptor) }
        return newTargetDescriptors.any { descriptor ->
            if (DESCRIPTOR_RENDERER_FOR_COMPARISON.render(descriptor) != renderedImportableTarget) return@any false
            if (importableDescriptor == targetDescriptor) return@any true
            val candidateDescriptors: Collection<DeclarationDescriptor> = when (targetDescriptor) {
                is ConstructorDescriptor -> {
                    (descriptor as? ClassDescriptor)?.constructors ?: emptyList()
                }
                is PropertyAccessorDescriptor -> {
                    (descriptor as? PropertyDescriptor)
                        ?.let { if (targetDescriptor is PropertyGetterDescriptor) it.getter else it.setter }
                        ?.let { listOf(it) }
                        ?: emptyList()
                }
                else -> emptyList()
            }
            candidateDescriptors.any { DESCRIPTOR_RENDERER_FOR_COMPARISON.render(it) == renderedTarget }
        }
    }

    val referencesToSkip = HashSet<KtReferenceExpression>()
    for (declaration in moveCheckerInfo.elementsToMove) {
        if (declaration.module == targetModule) continue
        declaration.forEachDescendantOfType<KtReferenceExpression> { refExpr ->
            // NB: for unknown reason, refExpr.resolveToCall() does not work here
            val targetDescriptor =
                refExpr.analyze(BodyResolveMode.PARTIAL)[BindingContext.REFERENCE_TARGET, refExpr] ?: return@forEachDescendantOfType
            if (KotlinBuiltIns.isBuiltIn(targetDescriptor)) return@forEachDescendantOfType
            val target = DescriptorToSourceUtilsIde.getAnyDeclaration(moveCheckerInfo.project, targetDescriptor) ?: return@forEachDescendantOfType
            if (moveCheckerInfo.isToBeMoved(target)) return@forEachDescendantOfType
            if (isInScope(target, targetDescriptor)) return@forEachDescendantOfType
            if (target is KtTypeParameter) return@forEachDescendantOfType
            val superMethods = SmartSet.create<PsiMethod>()
            target.toLightMethods().forEach { superMethods += it.findDeepestSuperMethods() }
            if (superMethods.any { isInScope(it, targetDescriptor) }) return@forEachDescendantOfType

            val refContainer = refExpr.getStrictParentOfType<KtNamedDeclaration>() ?: return@forEachDescendantOfType
            val scopeDescription = RefactoringUIUtil.getDescription(refContainer, true)
            val message = RefactoringBundle.message(
                "0.referenced.in.1.will.not.be.accessible.in.module.2",
                RefactoringUIUtil.getDescription(target, true),
                scopeDescription,
                CommonRefactoringUtil.htmlEmphasize(targetModule.name)
            )
            conflicts.putValue(target, StringUtil.capitalize(message))
            referencesToSkip += refExpr
        }
    }
    internalUsages.removeIf { it.reference?.element?.let { element -> element in referencesToSkip } ?: false }
    return conflicts
}

fun checkVisibilityInUsages(
    moveCheckerInfo: KotlinMoveConflictCheckerInfo,
    usages: Collection<UsageInfo>
): MultiMap<PsiElement, String> {
    val conflicts = MultiMap<PsiElement, String>()
    val declarationToContainers = HashMap<KtNamedDeclaration, MutableSet<PsiElement>>()
    for (usage in usages) {
        val element = usage.element
        if (element == null || usage !is MoveRenameUsageInfo || usage is NonCodeUsageInfo) continue
        if (moveCheckerInfo.isToBeMoved(element)) continue
        val referencedElement = usage.referencedElement?.namedUnwrappedElement as? KtNamedDeclaration ?: continue
        val referencedDescriptor = moveCheckerInfo.context.getResolutionFacade().resolveToDescriptor(referencedElement)
        val moveTarget = moveCheckerInfo.moveTarget
        if (referencedDescriptor is DeclarationDescriptorWithVisibility
            && referencedDescriptor.visibility == DescriptorVisibilities.PUBLIC
            && moveTarget is KotlinMoveTarget.ExistingElement
            && moveTarget.targetElement.parentsWithSelf.filterIsInstance<KtClassOrObject>().all { it.isPublic}
        ) continue
        val container = element.getUsageContext()
        if (!declarationToContainers.getOrPut(referencedElement) { HashSet() }.add(container)) continue
        val targetContainer = moveTarget.getContainerDescriptor(moveCheckerInfo.context) ?: continue
        val referencingDescriptor = when (container) {
            is KtDeclaration -> container.resolveToDescriptorIfAny()
            is PsiMember -> container.getJavaMemberDescriptor()
            else -> null
        } ?: continue
        val languageVersionSettings = referencedElement.getResolutionFacade().languageVersionSettings
        val actualVisibility = if (referencingDescriptor.isJavaDescriptor) referencedDescriptor.visibilityAsViewedFromJava() else null
        val originalDescriptorToCheck = referencedDescriptor.wrap(newVisibility = actualVisibility) ?: referencedDescriptor
        val newDescriptorToCheck = referencedDescriptor.asPredicted(targetContainer, actualVisibility) ?: continue
        if (originalDescriptorToCheck.isVisibleIn(referencingDescriptor, languageVersionSettings) && !newDescriptorToCheck.isVisibleIn(referencingDescriptor, languageVersionSettings)) {
            val message = KotlinBundle.message(
                "text.0.uses.1.which.will.be.inaccessible.after.move",
                render(container),
                render(referencedElement)
            ).replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }
            conflicts.putValue(element, message)
        }
    }
    return conflicts
}

fun checkVisibilityInDeclarations(moveCheckerInfo: KotlinMoveConflictCheckerInfo): MultiMap<PsiElement, String> {
    val conflicts = MultiMap<PsiElement, String>()
    val targetContainer = moveCheckerInfo.moveTarget.getContainerDescriptor(moveCheckerInfo.context) ?: return MultiMap.empty()

    fun DeclarationDescriptor.targetAwareContainingDescriptor(): DeclarationDescriptor? {
        val defaultContainer = containingDeclaration
        val psi = (this as? DeclarationDescriptorWithSource)?.source?.getPsi()
        return if (psi != null && psi in moveCheckerInfo.allElementsToMove) targetContainer else defaultContainer
    }

    fun DeclarationDescriptor.targetAwareContainers(): Sequence<DeclarationDescriptor> {
        return generateSequence(this) { it.targetAwareContainingDescriptor() }.drop(1)
    }

    fun DeclarationDescriptor.targetAwareContainingClass(): ClassDescriptor? {
        return targetAwareContainers().firstIsInstanceOrNull()
    }

    fun DeclarationDescriptorWithVisibility.isProtectedVisible(referrerDescriptor: DeclarationDescriptor): Boolean {
        val givenClassDescriptor = targetAwareContainingClass()
        val referrerClassDescriptor = referrerDescriptor.targetAwareContainingClass() ?: return false
        if (givenClassDescriptor != null && givenClassDescriptor.isCompanionObject) {
            val companionOwner = givenClassDescriptor.targetAwareContainingClass()
            if (companionOwner != null && referrerClassDescriptor.isSubclassOf(companionOwner)) return true
        }
        val whatDeclaration = DescriptorUtils.unwrapFakeOverrideToAnyDeclaration(this)
        val classDescriptor = whatDeclaration.targetAwareContainingClass() ?: return false
        if (referrerClassDescriptor.isSubclassOf(classDescriptor)) return true
        return referrerDescriptor.targetAwareContainingDescriptor()?.let { isProtectedVisible(it) } ?: false
    }

    fun DeclarationDescriptorWithVisibility.isVisibleFrom(ref: PsiReference, languageVersionSettings: LanguageVersionSettings): Boolean {
        val targetVisibility = visibility.normalize()
        if (targetVisibility == DescriptorVisibilities.PUBLIC) return true

        val refElement = ref.element
        val referrer = refElement.getStrictParentOfType<KtNamedDeclaration>()
        var referrerDescriptor = referrer?.resolveToDescriptorIfAny() ?: return true
        if (referrerDescriptor is ClassDescriptor && refElement.getParentOfTypeAndBranch<KtSuperTypeListEntry> { typeReference } != null) {
            referrerDescriptor.unsubstitutedPrimaryConstructor?.let { referrerDescriptor = it }
        }

        if (!isVisibleIn(referrerDescriptor, languageVersionSettings)) return true

        return when (targetVisibility) {
            DescriptorVisibilities.PROTECTED -> isProtectedVisible(referrerDescriptor)
            else -> isVisibleIn(targetContainer, languageVersionSettings)
        }
    }

    for (declaration in moveCheckerInfo.elementsToMove) {
        val languageVersionSettings = declaration.getResolutionFacade().languageVersionSettings
        declaration.forEachDescendantOfType<KtReferenceExpression> { refExpr ->
            refExpr.references.forEach { ref ->
                val target = ref.resolve() ?: return@forEach
                if (moveCheckerInfo.isToBeMoved(target)) return@forEach

                val targetDescriptor = when {
                    target is KtDeclaration -> target.resolveToDescriptorIfAny()
                    target is PsiMember && target.hasJavaResolutionFacade() -> target.getJavaMemberDescriptor()
                    else -> null
                } as? DeclarationDescriptorWithVisibility ?: return@forEach

                var isVisible = targetDescriptor.isVisibleFrom(ref, languageVersionSettings)
                if (isVisible && targetDescriptor is ConstructorDescriptor) {
                    isVisible = targetDescriptor.containingDeclaration.isVisibleFrom(ref, languageVersionSettings)
                }

                if (!isVisible) {
                    val message = KotlinBundle.message(
                        "text.0.uses.1.which.will.be.inaccessible.after.move",
                        render(declaration),
                        render(target)
                    )
                    conflicts.putValue(refExpr, message.replaceFirstChar(Char::uppercaseChar))
                }
            }
        }
    }
    return conflicts
}

fun checkInternalMemberUsages(moveCheckerInfo: KotlinMoveConflictCheckerInfo): MultiMap<PsiElement, String> {
    val conflicts = MultiMap<PsiElement, String>()
    val targetModule = moveCheckerInfo.moveTarget.getTargetModule(moveCheckerInfo.project) ?: return MultiMap.empty()

    val membersToCheck = LinkedHashSet<KtDeclaration>()
    val memberCollector = object : KtVisitorVoid() {
        override fun visitClassOrObject(classOrObject: KtClassOrObject) {
            val declarations = classOrObject.declarations
            declarations.filterTo(membersToCheck) { it.hasModifier(KtTokens.INTERNAL_KEYWORD) }
            declarations.forEach { it.accept(this) }
        }
    }
    moveCheckerInfo.elementsToMove.forEach { it.accept(memberCollector) }

    for (memberToCheck in membersToCheck) {
        for (reference in ReferencesSearch.search(memberToCheck).asIterable()) {
            val element = reference.element
            val usageModule = ModuleUtilCore.findModuleForPsiElement(element) ?: continue
            if (usageModule != targetModule && targetModule !in usageModule.implementedModules && !moveCheckerInfo.isToBeMoved(element)) {
                val container = element.getUsageContext()
                val message = KotlinBundle.message(
                    "text.0.uses.internal.1.which.will.be.inaccessible.after.move",
                    render(container),
                    render(memberToCheck)
                ).replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }
                conflicts.putValue(element, message)
            }
        }
    }
    return conflicts
}

fun checkSealedClassMove(moveCheckerInfo: KotlinMoveConflictCheckerInfo): MultiMap<PsiElement, String> {
    val languageVersionSettings = moveCheckerInfo.project.languageVersionSettings
    return if (languageVersionSettings.supportsFeature(LanguageFeature.AllowSealedInheritorsInDifferentFilesOfSamePackage)) {
        checkSealedClassMoveWithinPackageAndModule(moveCheckerInfo)
    } else {
        checkSealedClassMoveWithinFile(moveCheckerInfo)
    }
}

private fun checkSealedClassMoveWithinFile(moveChecker: KotlinMoveConflictCheckerInfo): MultiMap<PsiElement, String> {
    val conflicts = MultiMap<PsiElement, String>()
    val visited = HashSet<PsiElement>()
    for (elementToMove in moveChecker.elementsToMove) {
        if (!visited.add(elementToMove)) continue
        if (elementToMove !is KtClassOrObject) continue

        val rootClass: KtClass
        val rootClassDescriptor: ClassDescriptor
        if (elementToMove is KtClass && elementToMove.isSealed()) {
            rootClass = elementToMove
            rootClassDescriptor = rootClass.resolveToDescriptorIfAny() ?: return MultiMap.empty()
        } else {
            val classDescriptor = elementToMove.resolveToDescriptorIfAny() ?: return MultiMap.empty()
            val superClassDescriptor = classDescriptor.getSuperClassNotAny() ?: return MultiMap.empty()
            if (superClassDescriptor.modality != Modality.SEALED) return MultiMap.empty()
            rootClassDescriptor = superClassDescriptor
            rootClass = rootClassDescriptor.source.getPsi() as? KtClass ?: return MultiMap.empty()
        }

        val subclasses = rootClassDescriptor.sealedSubclasses.mapNotNull { it.source.getPsi() }
        if (subclasses.isEmpty()) continue

        visited.add(rootClass)
        visited.addAll(subclasses)

        if (moveChecker.isToBeMoved(rootClass) && subclasses.all { moveChecker.isToBeMoved(it) }) continue

        val message = if (elementToMove == rootClass) {
            KotlinBundle.message("text.sealed.class.0.must.be.moved.with.all.its.subclasses", rootClass.name.toString())
        } else {
            val type = ElementDescriptionUtil.getElementDescription(elementToMove, UsageViewTypeLocation.INSTANCE).replaceFirstChar {
                if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString()
            }
            KotlinBundle.message(
                "text.0.1.must.be.moved.with.sealed.parent.class.and.all.its.subclasses",
                type,
                rootClass.name.toString()
            )
        }
        conflicts.putValue(elementToMove, message)
    }
    return conflicts
}

private fun checkSealedClassMoveWithinPackageAndModule(moveChecker: KotlinMoveConflictCheckerInfo): MultiMap<PsiElement, String> {
    val conflicts = MultiMap<PsiElement, String>()
    val hierarchyChecker = SealedHierarchyChecker()
    for (elementToMove in moveChecker.elementsToMove) {
        if (elementToMove !is KtClassOrObject) continue
        hierarchyChecker.reportIfMoveIsDestructive(moveChecker, elementToMove)?.let { conflicts.putValue(elementToMove, it) }
    }
    return conflicts
}

fun checkNameClashes(moveCheckerInfo: KotlinMoveConflictCheckerInfo): MultiMap<PsiElement, String> {
    fun <T> equivalent(a: T, b: T): Boolean = when (a) {
        is DeclarationDescriptor -> when (a) {
            is FunctionDescriptor -> b is FunctionDescriptor
                    && equivalent(a.name, b.name) && a.valueParameters.zip(b.valueParameters).all { equivalent(it.first, it.second) }
            is ValueParameterDescriptor -> b is ValueParameterDescriptor
                    && equivalent(a.type, b.type)
            else -> b is DeclarationDescriptor && equivalent(a.name, b.name)
        }
        is Name -> b is Name && a.asString() == b.asString()
        is FqName -> b is FqName && a.asString() == b.asString()
        is KotlinType -> {
            if (b !is KotlinType) false
            else {
                val aSupertypes = a.constructor.supertypesWithAny()
                val bSupertypes = b.constructor.supertypesWithAny()
                when {
                    a.isAnyOrNullableAny() && b.isAnyOrNullableAny() ->         // a = T(?) | Any(?), b = T(?) | Any(?)
                        true                                                    // => 100% clash
                    aSupertypes.size == 1 && bSupertypes.size == 1 ->           // a = T: T1, b = T: T2
                        equivalent(aSupertypes.first(), bSupertypes.first())    // equivalent(T1, T2) => clash
                    a.arguments.isNotEmpty() && b.arguments.isNotEmpty() ->
                        equivalent(                                             // a = Something<....>, b = SomethingElse<....>
                            a.constructor.declarationDescriptor?.name,          // equivalent(Something, SomethingElse) => clash
                            b.constructor.declarationDescriptor?.name
                        )
                    else -> a == b                                              // common case. a == b => clash
                }
            }
        }
        else -> false
    }

    fun walkDeclarations(
        currentScopeDeclaration: DeclarationDescriptor,
        declaration: DeclarationDescriptor,
        report: (DeclarationDescriptor, DeclarationDescriptor) -> Unit
    ) {
        when (currentScopeDeclaration) {
            is PackageFragmentDescriptor -> {

                fun getPackage(declaration: PackageFragmentDescriptor) =
                    declaration.containingDeclaration.getPackage(declaration.fqName)

                val packageDescriptor = getPackage(currentScopeDeclaration)
                if ((declaration.containingDeclaration)?.fqNameOrNull() == currentScopeDeclaration.fqNameOrNull()) {
                    return
                }
                packageDescriptor
                    .memberScope
                    .getContributedDescriptors { it == declaration.name }
                    .filter { equivalent(it, declaration) }
                    .forEach { report(it, packageDescriptor) }
                return
            }
            is ClassDescriptor -> {
                if ((declaration.containingDeclaration)?.fqNameOrNull() != currentScopeDeclaration.fqNameOrNull()) {
                    currentScopeDeclaration
                        .unsubstitutedMemberScope
                        .getContributedDescriptors { it == declaration.name }
                        .filter { equivalent(it, declaration) }
                        .forEach { report(it, currentScopeDeclaration) }
                }
                return
            }

        }
        currentScopeDeclaration.containingDeclaration?.let { walkDeclarations(it, declaration, report) }
    }

    val conflicts = MultiMap<PsiElement, String>()
    (moveCheckerInfo.elementsToMove)
        .filterIsInstance<PsiNamedElement>()
        .forEach { declaration ->
            val declarationDescriptor =
                (declaration as KtElement).analyze().get(BindingContext.DECLARATION_TO_DESCRIPTOR, declaration)
            if (declarationDescriptor is DeclarationDescriptor) {

                moveCheckerInfo.moveTarget.getContainerDescriptor(moveCheckerInfo.context)?.let { baseDescriptor ->
                    walkDeclarations(baseDescriptor, declarationDescriptor) { conflictingDeclaration, conflictingScope ->
                        val message = KotlinBundle.message(
                            "text.declarations.clash.move.0.destination.1.declared.in.scope.2",
                            render(declarationDescriptor),
                            render(conflictingDeclaration),
                            render(conflictingScope)
                        )
                        conflicts.putValue(declaration, message)
                    }
                }
            }
        }
    return conflicts
}


private class SealedHierarchyChecker {
    private val visited: MutableSet<ClassDescriptor> = mutableSetOf()

    fun reportIfMoveIsDestructive(moveChecker: KotlinMoveConflictCheckerInfo, classToMove: KtClassOrObject): String? {
        val classToMoveDesc = classToMove.resolveToDescriptorIfAny() ?: return null
        if (classToMoveDesc in visited) return null

        val directSealedParents = classToMoveDesc.listDirectSealedParents()

        // Not a part of sealed hierarchy?
        if (!classToMoveDesc.isSealed() && directSealedParents.isEmpty())
            return null

        // Standalone sealed class: no sealed parents, no subclasses?
        if (classToMoveDesc.isSealed() && directSealedParents.isEmpty() && classToMoveDesc.listAllSubclasses().isEmpty())
            return null

        // Ok, we're dealing with sealed hierarchy member
        val otherHierarchyMembers = classToMoveDesc.listSealedHierarchyMembers().apply { remove(classToMove) }
        assert(otherHierarchyMembers.isNotEmpty())

        // Entire hierarchy is to be moved at once?
        if (otherHierarchyMembers.all { moveChecker.isToBeMoved(it) })
            return null

        // Hierarchy might be split (broken) (members reside in different packages) and we shouldn't prevent intention to fix it.
        // That is why it's ok to move the class to a package where at least one member of hierarchy resides. In case the hierarchy is
        // fully correct all its members share the same package.

        val targetModule = moveChecker.moveTarget.getTargetModule(moveChecker.project) ?: return null
        val targetPackage = moveChecker.moveTarget.getTargetPackage(moveChecker.project) ?: return null
        val targetDir = moveChecker.moveTarget.targetFileOrDir?.takeIf {
            it.isDirectory
        } ?: moveChecker.moveTarget.targetFileOrDir?.parent

        val className = classToMove.nameAsSafeName.asString()

        if (otherHierarchyMembers.none { it.residesIn(targetModule, targetPackage, targetDir) }) {
            val hierarchyMembers = buildList { add(classToMove); addAll(otherHierarchyMembers) }.toNamesList()
            return KotlinBundle.message(
                "text.sealed.broken.hierarchy.none.in.target",
                className, moveChecker.moveTarget.getPackageName(), targetModule.name, hierarchyMembers
            )
        }

        // Ok, class joins at least one member of the hierarchy. But probably it leaves the package where other members still exist.
        // It doesn't mean we should prevent such move, but it might be good for the user to be aware of the situation.

        val moduleToMoveFrom = classToMove.module ?: return null
        val packageToMoveFrom = classToMoveDesc.findPsiPackage(moduleToMoveFrom) ?: return null
        val directoryToMoveFrom = classToMove.containingKtFile.containingDirectory?.virtualFile

        val membersRemainingInOriginalPackage =
            otherHierarchyMembers.filter {
                it.residesIn(moduleToMoveFrom, packageToMoveFrom, directoryToMoveFrom) && !moveChecker.isToBeMoved(it)
            }.toList()

        if ((targetPackage != packageToMoveFrom || targetModule != moduleToMoveFrom) &&
            membersRemainingInOriginalPackage.any { !moveChecker.isToBeMoved(it) }
        ) {
            return KotlinBundle.message(
                "text.sealed.broken.hierarchy.still.in.source",
                className, packageToMoveFrom.getNameOrDefault(), moduleToMoveFrom.name, membersRemainingInOriginalPackage.toNamesList()
            )
        }

        return null
    }

    private fun KtClassOrObject.residesIn(targetModule: Module, targetPackage: PsiPackage, targetDir: VirtualFile?): Boolean {
        val myModule = module ?: return false
        val myPackage = descriptor?.findPsiPackage(myModule)
        val myDirectory = containingKtFile.containingDirectory?.virtualFile
        return myPackage == targetPackage && myModule == targetModule && myDirectory == targetDir
    }

    private fun DeclarationDescriptor.findPsiPackage(module: Module): PsiPackage? {
        val fqName = findPackage().fqName
        return KotlinJavaPsiFacade.getInstance(module.project).findPackage(fqName.asString(), GlobalSearchScope.moduleScope(module))
    }

    private fun KotlinMoveTarget.getTargetPackage(project: Project): PsiPackage? {

        fun tryGetPackageFromTargetContainer(): PsiPackage? {
            val fqName = targetContainerFqName ?: return null
            val module = getTargetModule(project) ?: return null
            return KotlinJavaPsiFacade.getInstance(project).findPackage(fqName.asString(), GlobalSearchScope.moduleScope(module))
        }

        return (this as? KotlinMoveTarget.Directory)?.targetFileOrDir?.toPsiDirectory(project)?.getPackage()
            ?: (this as? KotlinMoveTarget.DeferredFile)?.targetFileOrDir?.toPsiDirectory(project)?.getPackage()
            ?: tryGetPackageFromTargetContainer()
    }

    private fun KotlinMoveTarget.getPackageName(): String =
        targetContainerFqName?.asString()?.takeIf { it.isNotEmpty() } ?: "default" // PsiPackage might not exist by this moment

    private fun PsiPackage?.getNameOrDefault(): String = this?.qualifiedName?.takeIf { it.isNotEmpty() } ?: "default"

    private fun ClassDescriptor.listDirectSealedParents(): List<ClassDescriptor> = buildList {
        getSuperClassNotAny()?.takeIf { it.isSealed() }?.let { this.add(it) }
        getSuperInterfaces().filter { it.isSealed() }.let { this.addAll(it) }
    }

    private fun ClassDescriptor.listAllSubclasses(): List<ClassDescriptor> {
        val sealedKtClass = findPsi() as? KtClassOrObject ?: return emptyList()
        val lightClass = sealedKtClass.toLightClass() ?: return emptyList()
        val searchScope = GlobalSearchScope.projectScope(sealedKtClass.project)
        val searchParameters = SearchParameters(lightClass, searchScope, false, true, false)

        return ClassInheritorsSearch.search(searchParameters)
            .asIterable()
            .map mapper@{
                val resolutionFacade = it.javaResolutionFacade() ?: return@mapper null
                it.resolveToDescriptor(resolutionFacade)
            }.filterNotNull()
            .sortedBy(ClassDescriptor::getName)
    }

    private fun ClassDescriptor.listSealedHierarchyMembers(): MutableList<KtClassOrObject> {

        fun ClassDescriptor.listMembersInternal(members: MutableList<ClassDescriptor>) {
            val alreadyVisited = !visited.add(this)
            if (alreadyVisited) return

            if (isSealed()) {
                members.add(this)
                listDirectSealedParents().forEach { it.listMembersInternal(members) }
                listAllSubclasses().forEach { it.listMembersInternal(members) }
            } else {
                val directSuperSealed = listDirectSealedParents()
                if (directSuperSealed.isNotEmpty()) {
                    members.add(this)
                    directSuperSealed.forEach { it.listMembersInternal(members) }
                }
            }
        }

        val members = mutableListOf<ClassDescriptor>()
        listMembersInternal(members)
        return members.mapNotNull { it.findPsi() as? KtClassOrObject }.toMutableList()
    }

    private fun List<PsiElement>.toNamesList(): List<String> = mapNotNull { el -> el.kotlinFqName?.asString() }.toList()
}