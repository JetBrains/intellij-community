// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.base.fir.analysisApiProviders

import com.intellij.lang.java.JavaLanguage
import com.intellij.openapi.Disposable
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiManager
import com.intellij.psi.PsiTreeChangeAdapter
import com.intellij.psi.PsiTreeChangeEvent
import com.intellij.psi.impl.PsiTreeChangeEventImpl
import org.jetbrains.annotations.TestOnly
import org.jetbrains.kotlin.analysis.low.level.api.fir.element.builder.getNonLocalContainingInBodyDeclarationWith
import org.jetbrains.kotlin.analysis.low.level.api.fir.file.structure.isReanalyzableContainer
import org.jetbrains.kotlin.idea.KotlinLanguage
import org.jetbrains.kotlin.idea.base.util.module
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

internal class FirIdeModificationTrackerService(project: Project) : Disposable {
    init {
        PsiManager.getInstance(project).addPsiTreeChangeListener(Listener(), this)
    }

    private val _projectGlobalOutOfBlockInKotlinFilesModificationCount = AtomicLong()
    val projectGlobalOutOfBlockInKotlinFilesModificationCount: Long
        get() = _projectGlobalOutOfBlockInKotlinFilesModificationCount.get()

    fun getOutOfBlockModificationCountForModules(module: Module): Long =
        moduleModificationsState.getModificationsCountForModule(module)

    private val moduleModificationsState = ModuleModificationsState()

    override fun dispose() {}

    fun increaseModificationCountForAllModules() {
        _projectGlobalOutOfBlockInKotlinFilesModificationCount.incrementAndGet()
        moduleModificationsState.increaseModificationCountForAllModules()
    }

    @TestOnly
    fun increaseModificationCountForModule(module: Module) {
        moduleModificationsState.increaseModificationCountForModule(module)
    }

    private inner class Listener : PsiTreeChangeAdapter() {
        override fun childAdded(event: PsiTreeChangeEvent) {
            processChange(event)
        }

        override fun childReplaced(event: PsiTreeChangeEvent) {
            processChange(event)
        }

        override fun childMoved(event: PsiTreeChangeEvent) {
            processChange(event)
        }

        override fun childRemoved(event: PsiTreeChangeEvent) {
            processChange(event)
        }

        override fun childrenChanged(event: PsiTreeChangeEvent) {
            if ((event as PsiTreeChangeEventImpl).isGenericChange) return
            processChange(event)
        }

        override fun propertyChanged(event: PsiTreeChangeEvent) {
            processChange(event)
        }

        private fun processChange(event: PsiTreeChangeEvent) {
            val rootElement = event.parent ?: return

            if (!rootElement.isPhysical) {
                /**
                 * Element which do not belong to a project should not cause OOBM
                 */
                return
            }

            incrementModificationCountForSpecificElements(event.child, rootElement)
        }

        private fun incrementModificationCountForSpecificElements(child: PsiElement?, rootElement: PsiElement) {
            val isOutOfBlock = isOutOfBlockChange(rootElement, child)
            if (isOutOfBlock) {
                val module = rootElement.module
                if (module != null) {
                    moduleModificationsState.increaseModificationCountForModule(module)
                }
                _projectGlobalOutOfBlockInKotlinFilesModificationCount.incrementAndGet()
            }
        }

        private fun isOutOfBlockChange(rootElement: PsiElement, child: PsiElement?): Boolean {
            return when (rootElement.language) {
                KotlinLanguage.INSTANCE -> {
                    isOutOfBlockChange(child ?: rootElement)
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

        private fun isOutOfBlockChange(psi: PsiElement): Boolean {
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