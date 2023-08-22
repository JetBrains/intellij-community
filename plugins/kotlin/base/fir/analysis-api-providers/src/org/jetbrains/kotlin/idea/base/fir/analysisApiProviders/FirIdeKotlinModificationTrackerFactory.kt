// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.base.fir.analysisApiProviders

import com.intellij.java.library.JavaLibraryModificationTracker
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.ModificationTracker
import org.jetbrains.annotations.TestOnly
import org.jetbrains.kotlin.analysis.providers.KotlinModificationTrackerFactory

internal class FirIdeKotlinModificationTrackerFactory(private val project: Project) : KotlinModificationTrackerFactory() {
    private val projectOutOfBlockModificationTracker
        get() = FirIdeOutOfBlockModificationService.getInstance(project).projectOutOfBlockModificationTracker

    private val libraryModificationTracker
        get() = JavaLibraryModificationTracker.getInstance(project) as JavaLibraryModificationTracker

    override fun createProjectWideOutOfBlockModificationTracker(): ModificationTracker = projectOutOfBlockModificationTracker

    override fun createLibrariesWideModificationTracker(): ModificationTracker = libraryModificationTracker

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
