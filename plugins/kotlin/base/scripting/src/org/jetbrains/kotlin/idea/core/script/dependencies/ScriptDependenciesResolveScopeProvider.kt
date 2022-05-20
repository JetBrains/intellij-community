// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.core.script.dependencies

import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.ResolveScopeProvider
import com.intellij.psi.search.GlobalSearchScope
import org.jetbrains.kotlin.idea.base.scripting.projectStructure.ScriptDependenciesInfo
import org.jetbrains.kotlin.idea.core.script.ScriptConfigurationManager

class ScriptDependenciesResolveScopeProvider : ResolveScopeProvider() {
    override fun getResolveScope(file: VirtualFile, project: Project): GlobalSearchScope? {
        val manager = ScriptConfigurationManager.getInstance(project)
        if (manager.getAllScriptsDependenciesClassFiles().isEmpty()) return null

        if (file !in manager.getAllScriptsDependenciesClassFilesScope() && file !in manager.getAllScriptDependenciesSourcesScope()) {
            return null
        }

        val scope = GlobalSearchScope.union(
            arrayOf(
                GlobalSearchScope.fileScope(project, file),
                *ScriptDependenciesInfo.ForProject(project).dependencies().map { it.contentScope }.toTypedArray()
            )
        )

        return KotlinScriptSearchScope(project, scope)
    }
}