// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.base.analysis

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.ModificationTracker
import com.intellij.openapi.util.SimpleModificationTracker
import com.intellij.psi.PsiManager
import kotlinx.coroutines.CoroutineScope
import org.jetbrains.kotlin.analysis.api.platform.analysisMessageBus
import org.jetbrains.kotlin.analysis.api.platform.modification.*
import org.jetbrains.kotlin.idea.KotlinLanguage
import org.jetbrains.uast.UastLanguagePlugin

@Service(Service.Level.PROJECT)
class KotlinUastOutOfCodeBlockModificationTracker internal constructor(private val project: Project, coroutineScope: CoroutineScope) : ModificationTracker {
    private val modificationTracker = SimpleModificationTracker()

    @Volatile
    private var uastLanguageTrackers: List<ModificationTracker> = emptyList()

    init {
        refresh()

        UastLanguagePlugin.EP.addChangeListener(coroutineScope) { refresh() }
    }

    private fun refresh() {
        uastLanguageTrackers = getLanguageTrackers(project)
    }

    init {
        project.analysisMessageBus.connect(coroutineScope).subscribe(
            KotlinModificationEvent.TOPIC,
            KotlinModificationEventListener { event ->
                when (event) {
                    is KotlinModuleOutOfBlockModificationEvent,
                    KotlinGlobalSourceModuleStateModificationEvent,
                    KotlinGlobalSourceOutOfBlockModificationEvent,
                        -> modificationTracker.incModificationCount()

                    else -> {}
                }
            }
        )
    }

    override fun getModificationCount(): Long = modificationTracker.modificationCount + uastLanguageTrackers.sumOf { it.modificationCount }

    companion object {
        @JvmStatic
        fun getInstance(project: Project): KotlinUastOutOfCodeBlockModificationTracker =
            project.service()

        private fun getLanguageTrackers(project: Project): List<ModificationTracker> {
            val psiManager = PsiManager.getInstance(project)
            return UastLanguagePlugin.EP.extensionList
                .map(UastLanguagePlugin::language)
                .filter { it != KotlinLanguage.INSTANCE }
                .map { psiManager.modificationTracker.forLanguage(it) }
        }
    }
}
