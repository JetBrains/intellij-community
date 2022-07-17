// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.base.fe10.analysisApiProviders

import com.intellij.openapi.project.Project
import com.intellij.openapi.util.ModificationTracker
import org.jetbrains.kotlin.analysis.project.structure.KtSourceModule
import org.jetbrains.kotlin.analysis.providers.KotlinModificationTrackerFactory
import org.jetbrains.kotlin.idea.base.projectStructure.ideaModule
import org.jetbrains.kotlin.idea.caches.project.LibraryModificationTracker
import org.jetbrains.kotlin.idea.caches.trackers.KotlinCodeBlockModificationListener
import org.jetbrains.kotlin.idea.caches.trackers.KotlinModuleOutOfCodeBlockModificationTracker

internal class Fe10KotlinModificationTrackerFactory(private val project: Project) : KotlinModificationTrackerFactory() {
    override fun createProjectWideOutOfBlockModificationTracker(): ModificationTracker {
        return KotlinCodeBlockModificationListener.getInstance(project).kotlinOutOfCodeBlockTracker
    }

    override fun createModuleWithoutDependenciesOutOfBlockModificationTracker(module: KtSourceModule): ModificationTracker {
        return KotlinModuleOutOfCodeBlockModificationTracker(module.ideaModule)
    }

    override fun createLibrariesModificationTracker(): ModificationTracker {
        return LibraryModificationTracker.getInstance(project)
    }

    override fun incrementModificationsCount() {
        KotlinCodeBlockModificationListener.getInstance(project).incModificationCount()
        KotlinModuleOutOfCodeBlockModificationTracker.incrementModificationCountForAllModules(project)
    }
}