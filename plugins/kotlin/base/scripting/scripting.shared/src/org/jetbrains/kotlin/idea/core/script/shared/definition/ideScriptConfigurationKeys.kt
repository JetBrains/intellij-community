// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.core.script.shared.definition

import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.workspace.jps.entities.ModuleId
import org.jetbrains.annotations.Nls
import org.jetbrains.kotlin.idea.KotlinIcons
import org.jetbrains.kotlin.idea.base.projectStructure.RootKindFilter
import org.jetbrains.kotlin.idea.base.projectStructure.matches
import org.jetbrains.kotlin.idea.core.script.shared.KotlinBaseScriptingBundle
import org.jetbrains.kotlin.idea.core.script.v1.compilerAllowsScriptsInSourceRoots
import org.jetbrains.kotlin.idea.core.script.v1.isSupportedUnderSourceRoot
import java.io.File
import javax.swing.Icon
import kotlin.script.experimental.api.IdeScriptCompilationConfigurationKeys
import kotlin.script.experimental.api.ScriptCompilationConfiguration
import kotlin.script.experimental.api.fileExtension
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

val IdeScriptCompilationConfigurationKeys.kotlinScriptDefinitionInlayHint: Key<((ScriptCompilationConfiguration) -> String)?>
        by PropertiesCollection.key({ configuration ->
            val title = configuration[ScriptCompilationConfiguration.ide.kotlinScriptTemplate]?.title
            val displayName = title ?: ".${configuration[ScriptCompilationConfiguration.fileExtension] ?: "kts"}"
            KotlinBaseScriptingBundle.message("hints.codevision.script.definition", displayName)
        })

val IdeScriptCompilationConfigurationKeys.kotlinScriptTemplate: Key<KotlinScriptTemplate> by PropertiesCollection.key()

val IdeScriptCompilationConfigurationKeys.reloadable: Key<Boolean> by PropertiesCollection.key(defaultValue = true)

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