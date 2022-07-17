// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.base.fir.analysisApiProviders

import com.intellij.ProjectTopics
import com.intellij.lang.ASTNode
import com.intellij.lang.java.JavaLanguage
import com.intellij.openapi.Disposable
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ModuleRootEvent
import com.intellij.openapi.roots.ModuleRootListener
import com.intellij.pom.PomManager
import com.intellij.pom.PomModelAspect
import com.intellij.pom.event.PomModelEvent
import com.intellij.pom.event.PomModelListener
import com.intellij.pom.tree.TreeAspect
import com.intellij.pom.tree.events.TreeChangeEvent
import com.intellij.psi.PsiElement
import com.intellij.psi.impl.source.tree.FileElement
import org.jetbrains.annotations.TestOnly
import org.jetbrains.kotlin.analysis.low.level.api.fir.element.builder.getNonLocalContainingInBodyDeclarationWith
import org.jetbrains.kotlin.analysis.low.level.api.fir.file.structure.isReanalyzableContainer
import org.jetbrains.kotlin.idea.KotlinLanguage
import org.jetbrains.kotlin.idea.base.util.module
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

internal class FirIdeModificationTrackerService(project: Project) : Disposable {
    init {
        PomManager.getModel(project).addModelListener(Listener())
        subscribeForRootChanges(project)
    }

    private val _projectGlobalOutOfBlockInKotlinFilesModificationCount = AtomicLong()
    val projectGlobalOutOfBlockInKotlinFilesModificationCount: Long
        get() = _projectGlobalOutOfBlockInKotlinFilesModificationCount.get()

    fun getOutOfBlockModificationCountForModules(module: Module): Long =
        moduleModificationsState.getModificationsCountForModule(module)

    private val moduleModificationsState = ModuleModificationsState()
    private val treeAspect = TreeAspect.getInstance(project)

    override fun dispose() {}

    fun increaseModificationCountForAllModules() {
        _projectGlobalOutOfBlockInKotlinFilesModificationCount.incrementAndGet()
        moduleModificationsState.increaseModificationCountForAllModules()
    }

    @TestOnly
    fun increaseModificationCountForModule(module: Module) {
        moduleModificationsState.increaseModificationCountForModule(module)
    }


    private fun subscribeForRootChanges(project: Project) {
        project.messageBus.connect(this).subscribe(
            ProjectTopics.PROJECT_ROOTS,
            object : ModuleRootListener {
                override fun rootsChanged(event: ModuleRootEvent) = increaseModificationCountForAllModules()
            }
        )
    }

    private inner class Listener : PomModelListener {
        override fun isAspectChangeInteresting(aspect: PomModelAspect): Boolean =
            treeAspect == aspect

        override fun modelChanged(event: PomModelEvent) {
            val changeSet = event.getChangeSet(treeAspect) as TreeChangeEvent? ?: return
            val psi = changeSet.rootElement.psi
            val changedElements = changeSet.changedElements

            handleChangedElementsInAllModules(changedElements, changeSet, psi)
        }

        private fun handleChangedElementsInAllModules(
            changedElements: Array<out ASTNode>,
            changeSet: TreeChangeEvent,
            changeSetRootElementPsi: PsiElement
        ) {
            if (!changeSetRootElementPsi.isPhysical) {
                /**
                 * Element which do not belong to a project should not cause OOBM
                 */
                return
            }
            if (changedElements.isEmpty()) {
                incrementModificationCountForFileChange(changeSet)
            } else {
                incrementModificationCountForSpecificElements(changedElements, changeSet, changeSetRootElementPsi)
            }
        }

        private fun incrementModificationCountForSpecificElements(
            changedElements: Array<out ASTNode>,
            changeSet: TreeChangeEvent,
            changeSetRootElementPsi: PsiElement
        ) {
            require(changedElements.isNotEmpty())
            var isOutOfBlockChangeInAnyModule = false

            changedElements.forEach { element ->
                val isOutOfBlock = element.isOutOfBlockChange(changeSet, changeSetRootElementPsi)
                isOutOfBlockChangeInAnyModule = isOutOfBlockChangeInAnyModule || isOutOfBlock
                if (isOutOfBlock) {
                    incrementModificationTrackerForContainingModule(element)
                }
            }

            if (isOutOfBlockChangeInAnyModule) {
                _projectGlobalOutOfBlockInKotlinFilesModificationCount.incrementAndGet()
            }
        }

        private fun incrementModificationCountForFileChange(changeSet: TreeChangeEvent) {
            val fileElement = changeSet.rootElement as FileElement ?: return
            incrementModificationTrackerForContainingModule(fileElement)
            _projectGlobalOutOfBlockInKotlinFilesModificationCount.incrementAndGet()
        }

        private fun incrementModificationTrackerForContainingModule(element: ASTNode) {
            element.psi.module?.let { module ->
                moduleModificationsState.increaseModificationCountForModule(module)
            }
        }

        private fun ASTNode.isOutOfBlockChange(changeSet: TreeChangeEvent, changeSetRootElementPsi: PsiElement): Boolean {
            return when (changeSetRootElementPsi.language) {
                KotlinLanguage.INSTANCE -> {
                    val nodes = changeSet.getChangesByElement(this).affectedChildren
                     nodes.any(::isOutOfBlockChange)
                }
                JavaLanguage.INSTANCE -> {
                    true // TODO improve for Java KTIJ-21684
                }
                else -> {
                    // Any other language may cause OOBM in Kotlin too
                    true
                }
            }

        }

        private fun isOutOfBlockChange(node: ASTNode): Boolean {
            val psi = node.psi ?: return true
            if (!psi.isValid) {
                /**
                 * If PSI is not valid, well something bad happened, OOBM won't hurt
                 */
                return true
            }
            val container = psi.getNonLocalContainingInBodyDeclarationWith() ?: return true
            return !isReanalyzableContainer(container)
        }
    }
}

private class ModuleModificationsState {
    private val modificationCountForModule = ConcurrentHashMap<Module, ModuleModifications>()
    private val state = AtomicLong()

    fun getModificationsCountForModule(module: Module) = modificationCountForModule.compute(module) { _, modifications ->
        val currentState = state.get()
        when {
            modifications == null -> ModuleModifications(0, currentState)
            modifications.state == currentState -> modifications
            else -> ModuleModifications(modificationsCount = modifications.modificationsCount + 1, state = currentState)
        }
    }!!.modificationsCount

    fun increaseModificationCountForAllModules() {
        state.incrementAndGet()
    }

    fun increaseModificationCountForModule(module: Module) {
        modificationCountForModule.compute(module) { _, modifications ->
            val currentState = state.get()
            when (modifications) {
                null -> ModuleModifications(0, currentState)
                else -> ModuleModifications(modifications.modificationsCount + 1, currentState)
            }
        }
    }

    private data class ModuleModifications(val modificationsCount: Long, val state: Long)
}