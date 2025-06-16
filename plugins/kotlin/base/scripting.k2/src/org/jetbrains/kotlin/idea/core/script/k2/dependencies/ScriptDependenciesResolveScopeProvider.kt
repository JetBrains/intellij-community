// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.core.script.k2.dependencies

import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.ResolveScopeProvider
import com.intellij.psi.search.GlobalSearchScope
import org.jetbrains.kotlin.idea.base.projectStructure.RootKindFilter
import org.jetbrains.kotlin.idea.base.projectStructure.RootKindMatcher
import org.jetbrains.kotlin.idea.base.scripting.projectStructure.KotlinScriptSearchScope
import org.jetbrains.kotlin.idea.core.script.ScriptDependencyAware

/**
 * @see KotlinScriptResolveScopeProvider
 */
class ScriptDependenciesResolveScopeProvider : ResolveScopeProvider() {

    /**
     * @param file is a file belonging to dependencies of a script being analysed
     */
    override fun getResolveScope(file: VirtualFile, project: Project): GlobalSearchScope? {
        /*
        Unfortunately, we cannot limit the scope to the concrete script dependencies. There is no way to say, what .kts is being analysed.
        Some facts to consider:
        - dependencies are analysed first, .kts file itself goes last
        - multiple editors can be opened (selected is only one of them)
        */

        if (RootKindMatcher.matches(project, file, RootKindFilter.libraryFiles.copy(includeScriptDependencies = false)))
            return null

        if (ScriptDependencyAware.getInstance(project).getAllScriptsDependenciesClassFiles().isEmpty()) return null

        if (file !in ScriptDependencyAware.getInstance(project).getAllScriptsDependenciesClassFilesScope()
            && file !in ScriptDependencyAware.getInstance(project).getAllScriptDependenciesSourcesScope()) {
            return null
        }

        return KotlinScriptSearchScope(project, GlobalSearchScope.fileScope(project, file))
    }
}
