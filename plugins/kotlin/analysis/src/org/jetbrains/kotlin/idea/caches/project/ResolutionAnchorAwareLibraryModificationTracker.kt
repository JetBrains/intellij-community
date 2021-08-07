// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.caches.project

import com.intellij.openapi.util.ModificationTracker
import org.jetbrains.kotlin.idea.caches.resolve.ResolutionAnchorCacheService
import org.jetbrains.kotlin.idea.caches.trackers.getLatestModificationCount

class ResolutionAnchorAwareLibraryModificationTracker(
    private val libraryInfo: LibraryInfo,
) : ModificationTracker {
    override fun getModificationCount(): Long {
        val anchorDependencyModules = ResolutionAnchorCacheService.getInstance(libraryInfo.project)
            .getDependencyResolutionAnchors(libraryInfo)
            .map { it.module }
        return getLatestModificationCount(anchorDependencyModules)
    }
}
