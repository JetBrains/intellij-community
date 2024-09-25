// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.base.fir.analysisApiPlatform

import com.intellij.openapi.project.Project
import com.intellij.openapi.util.ModificationTracker
import org.jetbrains.kotlin.idea.base.projectStructure.ProjectStructureProviderService
import org.jetbrains.kotlin.idea.base.projectStructure.moduleInfo.LibraryInfo
import org.jetbrains.kotlin.idea.util.publishGlobalSourceOutOfBlockModification

internal class K2IdeProjectStructureProviderService(private val project: Project) : ProjectStructureProviderService {
    override fun createLibraryModificationTracker(libraryInfo: LibraryInfo): ModificationTracker {
        error("Should not be called for K2")
    }

    override fun incOutOfBlockModificationCount() {
        project.publishGlobalSourceOutOfBlockModification()
    }
}