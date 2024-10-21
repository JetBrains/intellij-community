// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.refactoring.move

import com.intellij.ide.util.DirectoryUtil
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Comparing
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.NlsContexts
import com.intellij.psi.*
import com.intellij.refactoring.RefactoringBundle
import com.intellij.refactoring.move.moveMembers.MoveMemberHandler
import com.intellij.refactoring.move.moveMembers.MoveMembersOptions
import com.intellij.refactoring.move.moveMembers.MoveMembersProcessor
import com.intellij.refactoring.util.MoveRenameUsageInfo
import com.intellij.refactoring.util.NonCodeUsageInfo
import com.intellij.usageView.UsageInfo
import com.intellij.util.IncorrectOperationException
import com.intellij.util.SmartList
import com.intellij.util.containers.addIfNotNull
import org.jetbrains.kotlin.descriptors.*
import org.jetbrains.kotlin.idea.KotlinFileType
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.base.util.isImported
import org.jetbrains.kotlin.idea.caches.resolve.analyze
import org.jetbrains.kotlin.idea.caches.resolve.analyzeWithAllCompilerChecks
import org.jetbrains.kotlin.idea.caches.resolve.analyzeWithContent
import org.jetbrains.kotlin.idea.caches.resolve.unsafeResolveToDescriptor
import org.jetbrains.kotlin.idea.codeInsight.DescriptorToSourceUtilsIde
import org.jetbrains.kotlin.idea.codeInsight.shorten.isToBeShortened
import org.jetbrains.kotlin.idea.core.util.toPsiDirectory
import org.jetbrains.kotlin.idea.imports.importableFqName
import org.jetbrains.kotlin.idea.references.KtSimpleNameReference
import org.jetbrains.kotlin.idea.references.mainReference
import org.jetbrains.kotlin.idea.search.usagesSearch.descriptor
import org.jetbrains.kotlin.idea.statistics.KotlinMoveRefactoringFUSCollector
import org.jetbrains.kotlin.idea.util.IdeDescriptorRenderers
import org.jetbrains.kotlin.load.java.descriptors.JavaCallableMemberDescriptor
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.collectDescendantsOfType
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
import org.jetbrains.kotlin.resolve.source.getPsi
import org.jetbrains.kotlin.types.expressions.DoubleColonLHS
import org.jetbrains.kotlin.util.capitalizeDecapitalize.capitalizeAsciiOnly
import java.io.File
import java.util.*
import kotlin.Throws

private val LOG = Logger.getInstance("#org.jetbrains.kotlin.idea.refactoring.move.MoveUtil")

internal var KtFile.allElementsToMove: List<PsiElement>? by UserDataProperty(Key.create("SCOPE_TO_MOVE"))

internal var KtSimpleNameExpression.internalUsageInfo: UsageInfo? by CopyablePsiUserDataProperty(Key.create("INTERNAL_USAGE_INFO"))

internal var KtFile.updatePackageDirective: Boolean? by UserDataProperty(Key.create("UPDATE_PACKAGE_DIRECTIVE"))

internal fun KtElement.getInternalReferencesToUpdateOnPackageNameChange(containerChangeInfo: MoveContainerChangeInfo): List<UsageInfo> {
    val usages = ArrayList<UsageInfo>()
    processInternalReferencesToUpdateOnPackageNameChange(this, containerChangeInfo) { expr, factory ->
        usages.addIfNotNull(factory(expr))
    }
    return usages
}

internal fun getTargetPackageFqName(targetContainer: PsiElement): FqName? {
    if (targetContainer is PsiDirectory) {
        val targetPackage = JavaDirectoryService.getInstance()?.getPackage(targetContainer)
        return if (targetPackage != null) FqName(targetPackage.qualifiedName) else null
    }
    return if (targetContainer is KtFile) targetContainer.packageFqName else null
}

internal fun markInternalUsages(usages: Collection<UsageInfo>) {
    usages.forEach { (it.element as? KtSimpleNameExpression)?.internalUsageInfo = it }
}

internal fun restoreInternalUsages(
    scope: KtElement,
    oldToNewElementsMapping: Map<PsiElement, PsiElement>,
    forcedRestore: Boolean = false
): List<UsageInfo> {
    return scope.collectDescendantsOfType<KtSimpleNameExpression>().mapNotNull {
        val usageInfo = it.internalUsageInfo
        if (!forcedRestore && usageInfo?.element != null) return@mapNotNull usageInfo
        val referencedElement = (usageInfo as? MoveRenameUsageInfo)?.referencedElement ?: return@mapNotNull null
        val newReferencedElement = mapToNewOrThis(referencedElement, oldToNewElementsMapping)
        if (!newReferencedElement.isValid) return@mapNotNull null
        (usageInfo as? KotlinMoveRenameUsage)?.refresh(it, newReferencedElement)
    }
}

internal fun cleanUpInternalUsages(usages: Collection<UsageInfo>) {
    usages.forEach { (it.element as? KtSimpleNameExpression)?.internalUsageInfo = null }
}

fun guessNewFileName(declarationsToMove: Collection<KtNamedDeclaration>): String? {
    if (declarationsToMove.isEmpty()) return null
    val representative = declarationsToMove.singleOrNull()
        ?: declarationsToMove.filterIsInstance<KtClassOrObject>().singleOrNull()
    val newFileName = representative?.run {
        if (containingKtFile.isScript()) "$name.kts" else "$name.${KotlinFileType.EXTENSION}"
    } ?: declarationsToMove.first().containingFile.name
    return newFileName.capitalizeAsciiOnly()
}

// returns true if successful
private fun updateJavaReference(reference: PsiReferenceExpression, oldElement: PsiElement, newElement: PsiElement): Boolean {
    if (oldElement is PsiMember && newElement is PsiMember) {
        // Remove import of old package facade, if any
        val oldClassName = oldElement.containingClass?.qualifiedName
        if (oldClassName != null) {
            val importOfOldClass = (reference.containingFile as? PsiJavaFile)?.importList?.allImportStatements?.firstOrNull {
                when (it) {
                    is PsiImportStatement -> it.qualifiedName == oldClassName
                    is PsiImportStaticStatement -> it.isOnDemand && it.importReference?.canonicalText == oldClassName
                    else -> false
                }
            }
            if (importOfOldClass != null && importOfOldClass.resolve() == null) {
                importOfOldClass.delete()
            }
        }

        val newClass = newElement.containingClass
        if (newClass != null && reference.qualifierExpression != null) {

            val refactoringOptions = object : MoveMembersOptions {
                override fun getMemberVisibility(): String = PsiModifier.PUBLIC
                override fun makeEnumConstant(): Boolean = true
                override fun getSelectedMembers(): Array<PsiMember> = arrayOf(newElement)
                override fun getTargetClassName(): String? = newClass.qualifiedName
            }

            val moveMembersUsageInfo = MoveMembersProcessor.MoveMembersUsageInfo(
                newElement, reference.element, newClass, reference.qualifierExpression, reference
            )

            val moveMemberHandler = MoveMemberHandler.EP_NAME.forLanguage(reference.element.language)
            if (moveMemberHandler != null) {
                moveMemberHandler.changeExternalUsage(refactoringOptions, moveMembersUsageInfo)
                return true
            }
        }
    }
    return false
}

internal fun mapToNewOrThis(e: PsiElement, oldToNewElementsMapping: Map<PsiElement, PsiElement>) = oldToNewElementsMapping[e] ?: e

private fun postProcessMoveUsage(
    usage: UsageInfo,
    oldToNewElementsMapping: Map<PsiElement, PsiElement>,
    nonCodeUsages: ArrayList<NonCodeUsageInfo>,
    shorteningMode: KtSimpleNameReference.ShorteningMode
) {
    if (usage is NonCodeUsageInfo) {
        nonCodeUsages.add(usage)
        return
    }

    if (usage !is MoveRenameUsageInfo) return

    val oldElement = usage.referencedElement!!
    val newElement = mapToNewOrThis(oldElement, oldToNewElementsMapping)

    when (usage) {
        is KotlinMoveRenameUsage.Deferred -> {
            val newUsage = usage.resolve(newElement) ?: return
            postProcessMoveUsage(newUsage, oldToNewElementsMapping, nonCodeUsages, shorteningMode)
        }

        is KotlinMoveRenameUsage.Unqualifiable -> {
            val file = with(usage) {
                if (addImportToOriginalFile) originalFile else mapToNewOrThis(
                    originalFile,
                    oldToNewElementsMapping
                )
            } as KtFile
            addDelayedImportRequest(newElement, file)
        }

        else -> {
            val reference = (usage.element as? KtSimpleNameExpression)?.mainReference ?: usage.reference
            processReference(reference, newElement, shorteningMode, oldElement)
        }
    }
}

private fun processReference(
    reference: PsiReference?,
    newElement: PsiElement,
    shorteningMode: KtSimpleNameReference.ShorteningMode,
    oldElement: PsiElement
) {
    try {
        when {
            reference is KtSimpleNameReference -> reference.bindToElement(newElement, shorteningMode)
            reference is PsiReferenceExpression && updateJavaReference(reference, oldElement, newElement) -> return
            else -> reference?.bindToElement(newElement)
        }
    } catch (e: IncorrectOperationException) {
        LOG.warn("bindToElement not implemented for ${reference!!::class.qualifiedName}")
    }
}

/**
 * Perform usage postprocessing and return non-code usages
 */
internal fun postProcessMoveUsages(
    usages: Collection<UsageInfo>,
    oldToNewElementsMapping: Map<PsiElement, PsiElement> = Collections.emptyMap(),
    shorteningMode: KtSimpleNameReference.ShorteningMode = KtSimpleNameReference.ShorteningMode.DELAYED_SHORTENING
): List<NonCodeUsageInfo> {
    val sortedUsages = usages.sortedWith(
        Comparator { o1, o2 ->
            val file1 = o1.virtualFile
            val file2 = o2.virtualFile
            if (Comparing.equal(file1, file2)) {
                val rangeInElement1 = o1.rangeInElement
                val rangeInElement2 = o2.rangeInElement
                if (rangeInElement1 != null && rangeInElement2 != null) {
                    return@Comparator rangeInElement2.startOffset - rangeInElement1.startOffset
                }
                return@Comparator 0
            }
            if (file1 == null) return@Comparator -1
            if (file2 == null) return@Comparator 1
            Comparing.compare(file1.path, file2.path)
        }
    )

    val nonCodeUsages = ArrayList<NonCodeUsageInfo>()

    val progressStep = 1.0 / sortedUsages.size
    val progressIndicator = ProgressManager.getInstance().progressIndicator
    progressIndicator?.isIndeterminate = false
    progressIndicator?.text = KotlinBundle.message("text.updating.usages.progress")
    usageLoop@ for ((i, usage) in sortedUsages.withIndex()) {
        progressIndicator?.fraction = (i + 1) * progressStep
        postProcessMoveUsage(usage, oldToNewElementsMapping, nonCodeUsages, shorteningMode)
    }
    progressIndicator?.text = ""

    return nonCodeUsages
}

internal fun collectOuterInstanceReferences(member: KtNamedDeclaration): List<OuterInstanceReferenceUsageInfo> {
    val result = SmartList<OuterInstanceReferenceUsageInfo>()
    traverseOuterInstanceReferences(member, false) { result += it }
    return result
}

@Throws(IncorrectOperationException::class)
internal fun getOrCreateDirectory(project: Project, path: String): PsiDirectory {
    File(path).toPsiDirectory(project)?.let { return it }
    return WriteCommandAction
        .writeCommandAction(project)
        .withName(RefactoringBundle.message("move.title"))
        .compute<PsiDirectory, Exception> {
            val fixUpSeparators = path.replace(File.separatorChar, '/')
            DirectoryUtil.mkdirs(PsiManager.getInstance(project), fixUpSeparators)
        }
}

internal fun <T> List<KtNamedDeclaration>.mapWithReadActionInProcess(
    project: Project,
    @NlsContexts.DialogTitle title: String,
    body: (KtNamedDeclaration) -> T
): List<T> = let { declarations ->
    val result = mutableListOf<T>()
    val task = object : Task.Modal(project, title, false) {
        override fun run(indicator: ProgressIndicator) {
            indicator.isIndeterminate = false
            indicator.fraction = 0.0
            val fraction = 1.0 / declarations.size
            runReadAction {
                declarations.forEachIndexed { index, declaration ->
                    result.add(body(declaration))
                    indicator.fraction = fraction * index
                }
            }
        }
    }
    ProgressManager.getInstance().run(task)
    return result
}

internal fun logFusForMoveRefactoring(
    numberOfEntities: Int,
    entity: KotlinMoveRefactoringFUSCollector.MovedEntity,
    destination: KotlinMoveRefactoringFUSCollector.MoveRefactoringDestination,
    isDefault: Boolean,
    body: Runnable
) {
    val timeStarted = System.currentTimeMillis()
    var succeeded = false
    try {
        body.run()
        succeeded = true
    } finally {
        KotlinMoveRefactoringFUSCollector.log(
            timeStarted = timeStarted,
            timeFinished = System.currentTimeMillis(),
            numberOfEntities = numberOfEntities,
            destination = destination,
            isDefault = isDefault,
            entity = entity,
            isSucceeded = succeeded,
        )
    }
}

internal fun isExtensionRef(expr: KtSimpleNameExpression): Boolean {
    val resolvedCall = expr.getResolvedCall(expr.analyze(BodyResolveMode.PARTIAL)) ?: return false
    if (resolvedCall is VariableAsFunctionResolvedCall) {
        return resolvedCall.variableCall.candidateDescriptor.isExtension || resolvedCall.functionCall.candidateDescriptor.isExtension
    }
    return resolvedCall.candidateDescriptor.isExtension
}

internal fun isQualifiable(callableReferenceExpression: KtCallableReferenceExpression): Boolean {
    val receiverExpression = callableReferenceExpression.receiverExpression
    val lhs = callableReferenceExpression.analyze(BodyResolveMode.PARTIAL)[BindingContext.DOUBLE_COLON_LHS, receiverExpression]
    return lhs is DoubleColonLHS.Type
}

fun traverseOuterInstanceReferences(
    member: KtNamedDeclaration,
    stopAtFirst: Boolean
) = traverseOuterInstanceReferences(member, stopAtFirst) { }

private fun traverseOuterInstanceReferences(
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

internal fun addDelayedImportRequest(elementToImport: PsiElement, file: KtFile) {
    org.jetbrains.kotlin.idea.codeInsight.shorten.addDelayedImportRequest(elementToImport, file)
}

internal fun addDelayedShorteningRequest(element: KtElement) {
    element.isToBeShortened = true
}

internal fun processInternalReferencesToUpdateOnPackageNameChange(
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
        val isExtension = isCallable && isExtensionRef(refExpr)
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
                    val psiClass = implicitClass?.source?.getPsi()
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

internal fun isValidTargetForImplicitCompanionAsDispatchReceiver(
    moveTarget: KotlinMoveTarget,
    companionObject: KtObjectDeclaration
): Boolean {
    return when (moveTarget) {
        is KotlinMoveTarget.Companion -> true
        is KotlinMoveTarget.ExistingElement -> {
            val targetClass = moveTarget.targetElement as? KtClassOrObject ?: return false
            val targetClassDescriptor = targetClass.unsafeResolveToDescriptor() as ClassDescriptor
            val companionClassDescriptor = companionObject.descriptor?.containingDeclaration as? ClassDescriptor ?: return false
            targetClassDescriptor.isSubclassOf(companionClassDescriptor)
        }
        else -> false
    }
}

internal fun renderType(classOrObject: KtClassOrObject): String {
    val type = (classOrObject.unsafeResolveToDescriptor() as ClassDescriptor).defaultType
    return IdeDescriptorRenderers.SOURCE_CODE.renderType(type)
}