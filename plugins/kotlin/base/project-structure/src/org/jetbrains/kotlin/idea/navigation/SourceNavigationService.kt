// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.navigation

import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.search.GlobalSearchScope

interface SourceNavigationService {
    fun targetClassFilesToSourcesScopes(virtualFile: VirtualFile, project: Project): List<GlobalSearchScope>

    companion object {
        fun getInstance(project: Project): SourceNavigationService = project.service()
    }
}