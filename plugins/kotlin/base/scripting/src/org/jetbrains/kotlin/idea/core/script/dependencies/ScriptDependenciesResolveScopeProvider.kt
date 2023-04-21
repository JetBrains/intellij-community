// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.core.script.dependencies

import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.ResolveScopeProvider
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.NonClasspathDirectoriesScope
import org.jetbrains.kotlin.idea.base.projectStructure.moduleInfo.SdkInfo
import org.jetbrains.kotlin.idea.base.projectStructure.scope.KotlinSourceFilterScope
import org.jetbrains.kotlin.idea.base.scripting.projectStructure.ScriptDependenciesInfo
import org.jetbrains.kotlin.idea.core.script.ScriptConfigurationManager
import org.jetbrains.kotlin.idea.core.script.ucache.*

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

        if (getAllScriptsDependenciesClassFiles(project).isEmpty()) return null

        if (file !in getAllScriptsDependenciesClassFilesScope(project)
            && file !in getAllScriptDependenciesSourcesScope(project)
        ) {
            return null
        }

        if (scriptsAsEntities) {
            val scripts = file.findDependentScripts(project) ?: return null
            val dependencies = scripts.flatMap { it.listDependencies(project, KotlinScriptLibraryRootTypeId.COMPILED) }.distinct()

            var searchScope = GlobalSearchScope.union(
                arrayOf(
                    GlobalSearchScope.fileScope(project, file),
                    KotlinSourceFilterScope.libraryClasses(NonClasspathDirectoriesScope.compose(dependencies), project)
                )
            )

            ScriptConfigurationManager.getInstance(project).getFirstScriptsSdk()?.let {
                searchScope = searchScope.uniteWith(SdkInfo(project, it).contentScope)
            }

            return searchScope
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
