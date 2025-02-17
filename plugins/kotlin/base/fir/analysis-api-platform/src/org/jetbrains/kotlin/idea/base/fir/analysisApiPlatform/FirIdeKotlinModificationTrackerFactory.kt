// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.base.fir.analysisApiPlatform

import com.intellij.java.library.JavaLibraryModificationTracker
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.ModificationTracker
import org.jetbrains.kotlin.analysis.api.platform.modification.KotlinModificationTrackerByEventFactoryBase

internal class FirIdeKotlinModificationTrackerFactory(private val project: Project) : KotlinModificationTrackerByEventFactoryBase(project) {
    override fun createLibrariesWideModificationTracker() = ModificationTracker {
        // We want the libraries-wide modification tracker to react to `JavaLibraryModificationTracker`, as this has historically been the
        // case and the modification tracker can e.g. be incremented in tests. We shouldn't use `JavaLibraryModificationTracker` as the
        // modification tracker that's incremented when receiving Kotlin modification events, because (1) its `incModificationCount`
        // function is test-only and (2) Kotlin modification events shouldn't affect IJ platform-wide modification trackers. Hence, we need
        // to provide a composite modification tracker here.
        eventLibrariesModificationTracker.modificationCount + JavaLibraryModificationTracker.getInstance(project).modificationCount
    }
}
