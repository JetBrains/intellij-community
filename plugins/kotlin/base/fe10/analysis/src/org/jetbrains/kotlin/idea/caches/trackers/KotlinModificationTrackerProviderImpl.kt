// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.caches.trackers

import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.ModificationTracker
import org.jetbrains.kotlin.idea.base.projectStructure.KotlinModificationTrackerProvider

class KotlinModificationTrackerProviderImpl(private val project: Project) : KotlinModificationTrackerProvider {
    override val projectTracker: ModificationTracker
        get() = KotlinCodeBlockModificationListener.getInstance(project).kotlinOutOfCodeBlockTracker

    override fun getModuleSelfModificationCount(module: Module): Long {
        return KotlinModuleOutOfCodeBlockModificationTracker.getUpdaterInstance(module.project).getModificationCount(module)
    }

    override fun createModuleModificationTracker(module: Module): ModificationTracker {
        return KotlinModuleOutOfCodeBlockModificationTracker(module)
    }
}