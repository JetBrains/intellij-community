// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.core.script.k1.configuration.listener

import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.extensions.ProjectExtensionPointName
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiManager
import org.jetbrains.kotlin.idea.base.projectStructure.RootKindFilter
import org.jetbrains.kotlin.idea.base.projectStructure.matches
import org.jetbrains.kotlin.idea.core.script.k1.configuration.DefaultScriptingSupport
import org.jetbrains.kotlin.psi.KtFile

/**
 * [org.jetbrains.kotlin.idea.core.script.k1.configuration.listener.ScriptChangesNotifier] will call first applicable [ScriptChangeListener] when editor is activated or document changed.
 * Listener should do something to invalidate configuration and schedule reloading.
 */
abstract class ScriptChangeListener(val project: Project) {
    val default: DefaultScriptingSupport
        get() = DefaultScriptingSupport.getInstance(project)

    abstract fun editorActivated(vFile: VirtualFile)
    abstract fun documentChanged(vFile: VirtualFile)

    abstract fun isApplicable(vFile: VirtualFile): Boolean

    protected fun getAnalyzableKtFileForScript(vFile: VirtualFile): KtFile? {
        return runReadAction {
            if (project.isDisposed) return@runReadAction null
            if (!vFile.isValid) return@runReadAction null

            return@runReadAction (PsiManager.getInstance(project).findFile(vFile) as? KtFile)
                ?.takeIf { RootKindFilter.projectSources.copy(includeScriptsOutsideSourceRoots = true).matches(it) }
        }
    }

    companion object {
        val LISTENER: ProjectExtensionPointName<ScriptChangeListener> =
            ProjectExtensionPointName("org.jetbrains.kotlin.scripting.idea.listener")
    }
}
