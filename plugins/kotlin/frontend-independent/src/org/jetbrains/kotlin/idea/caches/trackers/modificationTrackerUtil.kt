// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.caches.trackers

import com.intellij.openapi.module.Module
import com.intellij.openapi.util.ModificationTracker

fun getLatestModificationCount(modules: Collection<Module>): Long {
    if (modules.isEmpty())
        return ModificationTracker.NEVER_CHANGED.modificationCount

    val modificationCountUpdater =
        KotlinModuleOutOfCodeBlockModificationTracker.getUpdaterInstance(modules.first().project)
    return modules.maxOfOrNull { modificationCountUpdater.getModificationCount(it) }
        ?: ModificationTracker.NEVER_CHANGED.modificationCount
}
