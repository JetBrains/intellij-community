// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.base.projectStructure

import com.intellij.openapi.components.service
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.ModificationTracker

interface KotlinModificationTrackerProvider {
    companion object {
        fun getInstance(project: Project): KotlinModificationTrackerProvider = project.service()
    }

    val projectTracker: ModificationTracker

    fun getModuleSelfModificationCount(module: Module): Long

    fun createModuleModificationTracker(module: Module): ModificationTracker
}