// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.base.projectStructure.moduleInfo

import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.impl.libraries.LibraryEx
import com.intellij.platform.backend.workspace.workspaceModel
import com.intellij.platform.workspace.storage.EntitySource
import com.intellij.workspaceModel.ide.legacyBridge.findLibraryEntity
import org.jetbrains.kotlin.idea.base.util.K1ModeProjectStructureApi
import org.jetbrains.kotlin.platform.TargetPlatform
import org.jetbrains.kotlin.platform.jvm.JvmPlatforms

@K1ModeProjectStructureApi
class JvmLibraryInfo internal constructor(project: Project, library: LibraryEx) : LibraryInfo(project, library) {
    val source: EntitySource? = library.findLibraryEntity(project.workspaceModel.currentSnapshot)?.entitySource

    override val platform: TargetPlatform get() = JvmPlatforms.defaultJvmPlatform
}
