// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.caches.trackers

import com.intellij.openapi.project.Project
import com.intellij.openapi.util.ModificationTracker
import org.jetbrains.kotlin.psi.KtFile

class K1IDEKotlinModificationTrackerService(project: Project) : IDEKotlinModificationTrackerService(project) {
    override fun fileModificationTracker(file: KtFile): ModificationTracker =
        file.perFileModificationTracker
}