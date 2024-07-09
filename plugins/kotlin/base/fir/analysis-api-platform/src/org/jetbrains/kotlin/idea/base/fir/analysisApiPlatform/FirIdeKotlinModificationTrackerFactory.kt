// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.base.fir.analysisApiPlatform

import com.intellij.java.library.JavaLibraryModificationTracker
import com.intellij.openapi.Disposable
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.ModificationTracker
import com.intellij.openapi.util.SimpleModificationTracker
import org.jetbrains.annotations.TestOnly
import org.jetbrains.kotlin.analysis.api.platform.analysisMessageBus
import org.jetbrains.kotlin.analysis.api.platform.modification.KotlinGlobalSourceOutOfBlockModificationListener
import org.jetbrains.kotlin.analysis.api.platform.modification.KotlinModificationTopics
import org.jetbrains.kotlin.analysis.api.platform.modification.KotlinModificationTrackerFactory
import org.jetbrains.kotlin.analysis.api.platform.modification.KotlinModuleOutOfBlockModificationListener

internal class FirIdeKotlinModificationTrackerFactory(private val project: Project) : KotlinModificationTrackerFactory, Disposable {
    /**
     * A project-wide out-of-block modification tracker for Kotlin sources which will be incremented on global PSI tree changes, on any
     * module out-of-block modification, and by tests.
     */
    private val projectOutOfBlockModificationTracker: SimpleModificationTracker = SimpleModificationTracker()

    private val libraryModificationTracker
        get() = JavaLibraryModificationTracker.getInstance(project) as JavaLibraryModificationTracker

    init {
        val busConnection = project.analysisMessageBus.connect(this)

        busConnection.subscribe(
            KotlinModificationTopics.MODULE_OUT_OF_BLOCK_MODIFICATION,
            KotlinModuleOutOfBlockModificationListener { module -> projectOutOfBlockModificationTracker.incModificationCount() },
        )
        busConnection.subscribe(
            KotlinModificationTopics.GLOBAL_SOURCE_OUT_OF_BLOCK_MODIFICATION,
            KotlinGlobalSourceOutOfBlockModificationListener { projectOutOfBlockModificationTracker.incModificationCount() },
        )
    }

    override fun createProjectWideOutOfBlockModificationTracker(): ModificationTracker = projectOutOfBlockModificationTracker

    override fun createLibrariesWideModificationTracker(): ModificationTracker = libraryModificationTracker

    override fun dispose() {}

    @TestOnly
    internal fun incrementModificationsCount(includeBinaryTrackers: Boolean) {
        projectOutOfBlockModificationTracker.incModificationCount()
        if (includeBinaryTrackers) {
            libraryModificationTracker.incModificationCount()
        }
    }

    companion object {
        fun getInstance(project: Project): FirIdeKotlinModificationTrackerFactory =
            KotlinModificationTrackerFactory.getInstance(project) as FirIdeKotlinModificationTrackerFactory
    }
}
