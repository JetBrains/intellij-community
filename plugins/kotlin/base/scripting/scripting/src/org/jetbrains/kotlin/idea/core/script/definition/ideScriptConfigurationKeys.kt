// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.core.script.definition

import com.intellij.openapi.Disposable
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.workspace.jps.entities.ModuleId
import org.jetbrains.annotations.Nls
import org.jetbrains.kotlin.idea.KotlinIcons
import org.jetbrains.kotlin.idea.base.projectStructure.RootKindFilter
import org.jetbrains.kotlin.idea.base.projectStructure.matches
import org.jetbrains.kotlin.idea.core.script.compilerAllowsScriptsInSourceRoots
import org.jetbrains.kotlin.idea.core.script.isSupportedUnderSourceRoot
import org.jetbrains.kotlin.idea.core.script.notifications.DefaultScriptEditorToolbarProvider
import java.io.File
import javax.swing.Icon
import javax.swing.JComponent
import kotlin.script.experimental.api.IdeScriptCompilationConfigurationKeys
import kotlin.script.experimental.api.ScriptCompilationConfiguration
import kotlin.script.experimental.api.ide
import kotlin.script.experimental.api.isStandalone
import kotlin.script.experimental.jvm.jdkHome
import kotlin.script.experimental.jvm.jvm
import kotlin.script.experimental.util.PropertiesCollection
import kotlin.script.experimental.util.PropertiesCollection.Key


val IdeScriptCompilationConfigurationKeys.jdkSupplier: Key<(VirtualFile) -> File?> by PropertiesCollection.key(
    getDefaultValue = {
        { get(ScriptCompilationConfiguration.jvm.jdkHome) }
    }
)

/** The module a script belongs to. By default the module that contains the script, except for a standalone script under a source root. */
val IdeScriptCompilationConfigurationKeys.moduleSupplier: Key<(Project, VirtualFile) -> ModuleId?> by PropertiesCollection.key(
    getDefaultValue = {
        { project, virtualFile ->
            val standaloneUnderSourceRoot = !compilerAllowsScriptsInSourceRoots(project)
                    && get(ScriptCompilationConfiguration.isStandalone) == true
                    && !virtualFile.isSupportedUnderSourceRoot()
                    && RootKindFilter.projectSources.matches(project, virtualFile)
            if (standaloneUnderSourceRoot) null
            else ProjectFileIndex.getInstance(project).getModuleForFile(virtualFile)?.let { ModuleId(it.name) }
        }
    }
)

val IdeScriptCompilationConfigurationKeys.kotlinScriptTemplate: Key<KotlinScriptTemplate> by PropertiesCollection.key()

/** The action group that the default [editorToolbar] shows. */
const val SCRIPT_EDITOR_TOOLBAR_GROUP_ID: String = "KotlinScripts.EditorToolbar"

/** Provides the toolbar above the editor of a script file. */
fun interface ScriptEditorToolbarProvider {
    /** Returns null for no toolbar. The IDE disposes [lifetime] when it removes the toolbar. */
    fun provide(fileEditor: FileEditor, lifetime: Disposable): JComponent?
}

/** A null provider shows no toolbar. The key is transient, because a function does not serialize. */
val IdeScriptCompilationConfigurationKeys.editorToolbar: Key<ScriptEditorToolbarProvider?> by PropertiesCollection.key(
    defaultValue = DefaultScriptEditorToolbarProvider,
    isTransient = true,
)

val IdeScriptCompilationConfigurationKeys.canBeSwitchedOff: Key<Boolean> by PropertiesCollection.key(defaultValue = true)

data class KotlinScriptTemplate(var id: String = "") {
    @Nls
    var title: String = ""
    var templateName: String = "Kotlin Script"
    var icon: Icon = KotlinIcons.SCRIPT

    @Nls
    var description: String = ""
}


fun ScriptCompilationConfiguration.Builder.kotlinScriptTemplate(init: KotlinScriptTemplate.() -> Unit) {
    ide.kotlinScriptTemplate(KotlinScriptTemplate().apply(init))
}