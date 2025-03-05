// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.base.projectStructure.forwardDeclarations

import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.kotlin.analysis.api.projectStructure.KaModule

/**
 * A service for tracking the use of synthetic [VirtualFile]s for Kotlin/Native forward declarations by libraries of a project.
 */
@ApiStatus.Internal
interface KotlinForwardDeclarationsFileOwnerTracker {
    fun getFileOwner(virtualFile: VirtualFile): KaModule?
    fun registerFileOwner(virtualFile: VirtualFile, owner: KaModule)

    companion object {
        fun getInstance(project: Project): KotlinForwardDeclarationsFileOwnerTracker = project.service()
    }
}
