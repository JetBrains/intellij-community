// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.base.projectStructure

import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.ModificationTracker
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.kotlin.idea.base.projectStructure.moduleInfo.LibraryInfo

@ApiStatus.Internal
/**
 * A temp service needed during migrating [ProjectStructureProviderIdeImpl] from [org.jetbrains.kotlin.idea.base.projectStructure.moduleInfo.IdeaModuleInfo] to workspace model
 */
interface ProjectStructureProviderService {
    fun createLibraryModificationTracker(libraryInfo: LibraryInfo): ModificationTracker

    fun incOutOfBlockModificationCount()

    companion object {
        fun getInstance(project: Project): ProjectStructureProviderService = project.service()
    }
}