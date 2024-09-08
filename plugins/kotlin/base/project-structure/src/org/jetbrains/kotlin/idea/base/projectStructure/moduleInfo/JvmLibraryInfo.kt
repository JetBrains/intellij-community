// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.base.projectStructure.moduleInfo

import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.impl.libraries.LibraryEx
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.isFile
import com.intellij.platform.backend.workspace.virtualFile
import com.intellij.platform.backend.workspace.workspaceModel
import com.intellij.workspaceModel.ide.legacyBridge.findLibraryEntity
import org.jetbrains.kotlin.platform.TargetPlatform
import org.jetbrains.kotlin.platform.jvm.JvmPlatforms

class JvmLibraryInfo internal constructor(project: Project, library: LibraryEx) : LibraryInfo(project, library) {

    val librarySourceFile: VirtualFile? =
        library.findLibraryEntity(project.workspaceModel.currentSnapshot)
            ?.entitySource?.virtualFileUrl?.virtualFile?.takeIf(VirtualFile::isFile)

    override val platform: TargetPlatform get() = JvmPlatforms.defaultJvmPlatform

}