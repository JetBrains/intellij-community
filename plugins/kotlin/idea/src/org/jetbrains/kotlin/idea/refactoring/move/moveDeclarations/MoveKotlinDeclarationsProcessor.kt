// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.refactoring.move.moveDeclarations

import com.intellij.ide.IdeDeprecatedMessagesBundle
import com.intellij.ide.highlighter.JavaFileType
import com.intellij.ide.util.EditorHelper
import com.intellij.openapi.util.IntellijInternalApi
import com.intellij.openapi.util.Ref
import com.intellij.openapi.util.text.StringUtil
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiNamedElement
import com.intellij.psi.PsiReference
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.searches.ReferencesSearch
import com.intellij.refactoring.BaseRefactoringProcessor
import com.intellij.refactoring.move.MoveMultipleElementsViewDescriptor
import com.intellij.refactoring.move.moveClassesOrPackages.MoveClassHandler
import com.intellij.refactoring.rename.RenameUtil
import com.intellij.refactoring.util.NonCodeUsageInfo
import com.intellij.refactoring.util.RefactoringUIUtil
import com.intellij.refactoring.util.TextOccurrencesUtil
import com.intellij.usageView.UsageInfo
import com.intellij.usageView.UsageViewDescriptor
import com.intellij.usageView.UsageViewUtil
import com.intellij.util.IncorrectOperationException
import com.intellij.util.containers.CollectionFactory
import com.intellij.util.containers.HashingStrategy
import com.intellij.util.containers.MultiMap
import org.jetbrains.kotlin.asJava.classes.KtLightClassForFacade
import org.jetbrains.kotlin.asJava.elements.KtLightDeclaration
import org.jetbrains.kotlin.asJava.findFacadeClass
import org.jetbrains.kotlin.asJava.namedUnwrappedElement
import org.jetbrains.kotlin.asJava.toLightElements
import org.jetbrains.kotlin.idea.base.psi.kotlinFqName
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.base.util.module
import org.jetbrains.kotlin.idea.base.util.projectScope
import org.jetbrains.kotlin.idea.base.util.quoteIfNeeded
import org.jetbrains.kotlin.idea.base.util.restrictByFileType
import org.jetbrains.kotlin.idea.codeInsight.shorten.addToBeShortenedDescendantsToWaitingSet
import org.jetbrains.kotlin.idea.codeInsight.shorten.performDelayedRefactoringRequests
import org.jetbrains.kotlin.idea.refactoring.broadcastRefactoringExit
import org.jetbrains.kotlin.idea.refactoring.move.*
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.psi.KtElement
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtNamedDeclaration
import org.jetbrains.kotlin.psi.psiUtil.isAncestor
import org.jetbrains.kotlin.utils.ifEmpty
import org.jetbrains.kotlin.utils.keysToMap
import kotlin.math.max
import kotlin.math.min

private object ElementHashingStrategy : HashingStrategy<PsiElement> {
    override fun equals(e1: PsiElement?, e2: PsiElement?): Boolean {
        if (e1 === e2) return true
        // Name should be enough to distinguish different light elements based on the same original declaration
        if (e1 is KtLightDeclaration<*, *> && e2 is KtLightDeclaration<*, *>) {
            return e1.kotlinOrigin == e2.kotlinOrigin && e1.name == e2.name
        }
        return false
    }

    override fun hashCode(e: PsiElement?): Int {
        return when (e) {
            null -> 0
            is KtLightDeclaration<*, *> -> (e.kotlinOrigin?.hashCode() ?: 0) * 31 + (e.name?.hashCode() ?: 0)
            else -> e.hashCode()
        }
    }
}

@IntellijInternalApi
open class MoveKotlinDeclarationsProcessor(
    val descriptor: MoveDeclarationsDescriptor,
    val mover: KotlinMover = KotlinMover.Default,
    private val throwOnConflicts: Boolean = false
) : BaseRefactoringProcessor(descriptor.project) {
    companion object {
        const val REFACTORING_ID = "move.kotlin.declarations"
    }

    val project get() = descriptor.project

    private var nonCodeUsages: Array<NonCodeUsageInfo>? = null
    private val moveEntireFile = descriptor.moveSource is KotlinMoveSource.File
    private val elementsToMove = descriptor.moveSource.elementsToMove.filter { e ->
        e.parent != descriptor.moveTarget.getTargetPsiIfExists(e)
    }

    private val kotlinToLightElementsBySourceFile = elementsToMove
        .groupBy { it.containingKtFile }
        .mapValues { it.value.keysToMap { declaration -> declaration.toLightElements().ifEmpty { listOf(declaration) } } }
    private val conflicts = MultiMap<PsiElement, String>()

    override fun getRefactoringId() = REFACTORING_ID

    override fun createUsageViewDescriptor(usages: Array<out UsageInfo>): UsageViewDescriptor {
        val targetContainerFqName = descriptor.moveTarget.targetContainerFqName?.let {
            if (it.isRoot) IdeDeprecatedMessagesBundle.message("default.package.presentable.name") else it.asString()
        } ?: IdeDeprecatedMessagesBundle.message("default.package.presentable.name")
        return MoveMultipleElementsViewDescriptor(elementsToMove.toTypedArray(), targetContainerFqName)
    }

    fun getConflictsAsUsages(): List<UsageInfo> = conflicts.entrySet().map { MoveConflictUsageInfo(it.key, it.value) }

    public override fun findUsages(): Array<UsageInfo> {
        if (!descriptor.searchReferences || elementsToMove.isEmpty()) return UsageInfo.EMPTY_ARRAY

        val newContainerName = descriptor.moveTarget.targetContainerFqName?.asString() ?: ""

        fun getSearchScope(element: PsiElement): GlobalSearchScope? {
            val projectScope = project.projectScope()
            val ktDeclaration = element.namedUnwrappedElement as? KtNamedDeclaration ?: return projectScope
            if (ktDeclaration.hasModifier(KtTokens.PRIVATE_KEYWORD)) return projectScope
            val moveTarget = descriptor.moveTarget
            val (oldContainer, newContainer) = descriptor.delegate.getContainerChangeInfo(ktDeclaration, moveTarget)
            val targetModule = moveTarget.getTargetModule(project) ?: return projectScope
            if (oldContainer != newContainer || ktDeclaration.module != targetModule) return projectScope
            // Check if facade class may change
            if (newContainer is MoveContainerInfo.Package) {
                val javaScope = projectScope.restrictByFileType(JavaFileType.INSTANCE)
                val currentFile = ktDeclaration.containingKtFile
                val newFile = when (moveTarget) {
                    is KotlinMoveTarget.ExistingElement -> moveTarget.targetElement as? KtFile ?: return null
                    is KotlinMoveTarget.DeferredFile -> return javaScope
                    else -> return null
                }
                val currentFacade = currentFile.findFacadeClass()
                val newFacade = newFile.findFacadeClass()
                return if (currentFacade?.qualifiedName != newFacade?.qualifiedName) javaScope else null
            }
            return null
        }

        fun UsageInfo.intersectsWith(usage: UsageInfo): Boolean {
            if (element?.containingFile != usage.element?.containingFile) return false
            val firstSegment = segment ?: return false
            val secondSegment = usage.segment ?: return false
            return max(firstSegment.startOffset, secondSegment.startOffset) <= min(firstSegment.endOffset, secondSegment.endOffset)
        }

        fun collectUsages(kotlinToLightElements: Map<KtNamedDeclaration, List<PsiNamedElement>>, result: MutableCollection<UsageInfo>) {
            kotlinToLightElements.values.flatten().flatMapTo(result) { lightElement ->
                val searchScope = getSearchScope(lightElement) ?: return@flatMapTo emptyList()
                val elementName = lightElement.name ?: return@flatMapTo emptyList()
                val newFqName = StringUtil.getQualifiedName(newContainerName, elementName)

                val foundReferences = HashSet<PsiReference>()
                val results = ReferencesSearch
                    .search(lightElement, searchScope)
                    .mapNotNullTo(ArrayList()) { ref ->
                        if (foundReferences.add(ref) && elementsToMove.none { it.isAncestor(ref.element) }) {
                            KotlinMoveRenameUsage.createIfPossible(ref, lightElement, addImportToOriginalFile = true, isInternal = false)
                        } else null
                    }

              val name = lightElement.kotlinFqName?.quoteIfNeeded()?.asString()
                if (name != null) {
                    fun searchForKotlinNameUsages(results: ArrayList<UsageInfo>) {
                        TextOccurrencesUtil.findNonCodeUsages(
                            lightElement,
                            searchScope,
                            name,
                            descriptor.searchInCommentsAndStrings,
                            descriptor.searchInNonCode,
                            FqName(newFqName).quoteIfNeeded().asString(),
                            results
                        )
                    }

                    val facadeContainer = lightElement.parent as? KtLightClassForFacade
                    if (facadeContainer != null) {
                        val oldFqNameWithFacade = StringUtil.getQualifiedName(facadeContainer.qualifiedName, elementName)
                        val newFqNameWithFacade = StringUtil.getQualifiedName(
                            StringUtil.getQualifiedName(newContainerName, facadeContainer.name),
                            elementName
                        )

                        TextOccurrencesUtil.findNonCodeUsages(
                            lightElement,
                            searchScope,
                            oldFqNameWithFacade,
                            descriptor.searchInCommentsAndStrings,
                            descriptor.searchInNonCode,
                            FqName(newFqNameWithFacade).quoteIfNeeded().asString(),
                            results
                        )

                        ArrayList<UsageInfo>().also { searchForKotlinNameUsages(it) }.forEach { kotlinNonCodeUsage ->
                            if (results.none { it.intersectsWith(kotlinNonCodeUsage) }) {
                                results.add(kotlinNonCodeUsage)
                            }
                        }
                    } else {
                        searchForKotlinNameUsages(results)
                    }
                }

                MoveClassHandler.EP_NAME.extensions.forEach { handler ->
                    handler.preprocessUsages(results)
                }

                results
            }
        }

        val usages = ArrayList<UsageInfo>()
        val moveCheckerInfo = KotlinMoveConflictCheckerInfo(
            project,
            elementsToMove,
            descriptor.moveTarget,
            elementsToMove.first(),
            allElementsToMove = descriptor.allElementsToMove
        )
        for ((sourceFile, kotlinToLightElements) in kotlinToLightElementsBySourceFile) {
            val internalUsages = LinkedHashSet<UsageInfo>()
            val externalUsages = LinkedHashSet<UsageInfo>()

            if (moveEntireFile) {
                val changeInfo = MoveContainerChangeInfo(
                    MoveContainerInfo.Package(sourceFile.packageFqName),
                    descriptor.moveTarget.targetContainerFqName?.let { MoveContainerInfo.Package(it) } ?: MoveContainerInfo.UnknownPackage
                )
                internalUsages += sourceFile.getInternalReferencesToUpdateOnPackageNameChange(changeInfo)
            } else {
                kotlinToLightElements.keys.forEach {
                    val packageNameInfo = descriptor.delegate.getContainerChangeInfo(it, descriptor.moveTarget)
                    internalUsages += it.getInternalReferencesToUpdateOnPackageNameChange(packageNameInfo)
                }
            }

            internalUsages += descriptor.delegate.findInternalUsages(descriptor.moveSource)
            collectUsages(kotlinToLightElements, externalUsages)
            if (descriptor.analyzeConflicts) {
                conflicts.putAllValues(checkAllConflicts(moveCheckerInfo, internalUsages, externalUsages))
                descriptor.delegate.collectConflicts(descriptor.moveTarget, internalUsages, conflicts)
            }

            usages += internalUsages
            usages += externalUsages
        }

        return UsageViewUtil.removeDuplicatedUsages(usages.toTypedArray())
    }

    protected override fun preprocessUsages(refUsages: Ref<Array<UsageInfo>>): Boolean {
        return showConflicts(conflicts, refUsages.get())
    }

    override fun showConflicts(conflicts: MultiMap<PsiElement, String>, usages: Array<out UsageInfo>?): Boolean {
        if (throwOnConflicts && !conflicts.isEmpty) throw MoveConflictsFoundException()
        return super.showConflicts(conflicts, usages)
    }

    override fun performRefactoring(usages: Array<out UsageInfo>) = doPerformRefactoring(usages.toList())

    internal fun doPerformRefactoring(usages: List<UsageInfo>) {
        fun moveDeclaration(declaration: KtNamedDeclaration, moveTarget: KotlinMoveTarget): KtNamedDeclaration {
            val targetContainer = moveTarget.getOrCreateTargetPsi(declaration)
            descriptor.delegate.preprocessDeclaration(descriptor.moveTarget, declaration)
            if (moveEntireFile) return declaration
            return mover(declaration, targetContainer).apply {
                addToBeShortenedDescendantsToWaitingSet()
            }
        }

        val (oldInternalUsages, externalUsages) = usages.partition { it is KotlinMoveRenameUsage && it.isInternal }
        val newInternalUsages = ArrayList<UsageInfo>()

        markInternalUsages(oldInternalUsages)

        val usagesToProcess = ArrayList(externalUsages)

        try {
            descriptor.delegate.preprocessUsages(project, descriptor.moveSource, usages)

            val oldToNewElementsMapping = CollectionFactory.createCustomHashingStrategyMap<PsiElement, PsiElement>(ElementHashingStrategy)

            val newDeclarations = ArrayList<KtNamedDeclaration>()

            for ((sourceFile, kotlinToLightElements) in kotlinToLightElementsBySourceFile) {
                for ((oldDeclaration, oldLightElements) in kotlinToLightElements) {
                    val elementListener = transaction?.getElementListener(oldDeclaration)

                    val newDeclaration = moveDeclaration(oldDeclaration, descriptor.moveTarget)
                    newDeclarations += newDeclaration

                    oldToNewElementsMapping[oldDeclaration] = newDeclaration
                    oldToNewElementsMapping[sourceFile] = newDeclaration.containingKtFile

                    elementListener?.elementMoved(newDeclaration)
                    for ((oldElement, newElement) in oldLightElements.asSequence().zip(newDeclaration.toLightElements().asSequence())) {
                        oldToNewElementsMapping[oldElement] = newElement
                    }

                    if (descriptor.openInEditor) {
                        EditorHelper.openInEditor(newDeclaration)
                    }
                }

                if (descriptor.deleteSourceFiles && sourceFile.declarations.isEmpty()) {
                    sourceFile.delete()
                }
            }

            val internalUsageScopes: List<KtElement> = if (moveEntireFile) {
                newDeclarations.asSequence().map { it.containingKtFile }.distinct().toList()
            } else {
                newDeclarations
            }
            internalUsageScopes.forEach { newInternalUsages += restoreInternalUsages(it, oldToNewElementsMapping) }

            usagesToProcess += newInternalUsages
            nonCodeUsages = postProcessMoveUsages(usagesToProcess, oldToNewElementsMapping).toTypedArray()
            performDelayedRefactoringRequests(project)
        } catch (e: IncorrectOperationException) {
            nonCodeUsages = null
            RefactoringUIUtil.processIncorrectOperation(myProject, e)
        } finally {
            cleanUpInternalUsages(newInternalUsages + oldInternalUsages)
        }
    }

    override fun performPsiSpoilingRefactoring() {
        nonCodeUsages?.let { nonCodeUsages -> RenameUtil.renameNonCodeUsages(myProject, nonCodeUsages) }
        descriptor.moveCallback?.refactoringCompleted()
    }

    fun execute(usages: List<UsageInfo>) {
        execute(usages.toTypedArray())
    }

    override fun doRun() {
        try {
            super.doRun()
        } finally {
            broadcastRefactoringExit(myProject, refactoringId)
        }
    }

    override fun getCommandName(): String = KotlinBundle.message("command.move.declarations")
}
