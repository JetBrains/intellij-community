// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.base.projectStructure

import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.search.GlobalSearchScope
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
interface LibrarySourceScopeService {
    fun targetClassFilesToSourcesScopes(virtualFile: VirtualFile, project: Project): List<GlobalSearchScope>

    companion object {
        fun getInstance(project: Project): LibrarySourceScopeService = project.service<LibrarySourceScopeService>()
    }
}