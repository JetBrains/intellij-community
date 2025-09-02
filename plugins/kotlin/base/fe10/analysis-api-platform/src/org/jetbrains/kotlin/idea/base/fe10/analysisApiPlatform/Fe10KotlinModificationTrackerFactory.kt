// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.base.fe10.analysisApiPlatform

import com.intellij.java.library.JavaLibraryModificationTracker
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.ModificationTracker
import org.jetbrains.kotlin.analysis.api.platform.modification.KotlinModificationTrackerFactory
import org.jetbrains.kotlin.idea.caches.trackers.KotlinCodeBlockModificationListener

internal class Fe10KotlinModificationTrackerFactory(private val project: Project) : KotlinModificationTrackerFactory {
    override fun createProjectWideSourceModificationTracker(): ModificationTracker =
        KotlinCodeBlockModificationListener.getInstance(project).kotlinOutOfCodeBlockTracker

    override fun createProjectWideLibraryModificationTracker(): ModificationTracker =
        JavaLibraryModificationTracker.getInstance(project)
}
