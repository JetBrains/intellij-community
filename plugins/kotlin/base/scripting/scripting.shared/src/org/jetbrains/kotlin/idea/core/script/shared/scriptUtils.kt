// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.core.script.shared

import com.intellij.openapi.application.Application
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.extensions.ProjectExtensionPointName
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiManager
import org.jetbrains.annotations.TestOnly
import org.jetbrains.kotlin.idea.KotlinIcons
import org.jetbrains.kotlin.idea.core.script.v1.IdeScriptDefinitionProvider
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.NotNullableUserDataProperty
import org.jetbrains.kotlin.scripting.definitions.ScriptDefinition
import org.jetbrains.kotlin.scripting.definitions.ScriptDefinitionsSource
import org.jetbrains.kotlin.scripting.resolve.ScriptCompilationConfigurationWrapper
import javax.swing.Icon
import kotlin.script.experimental.api.IdeScriptCompilationConfigurationKeys
import kotlin.script.experimental.api.ScriptCompilationConfiguration
import kotlin.script.experimental.api.ScriptDiagnostic
import kotlin.script.experimental.api.defaultImports
import kotlin.script.experimental.api.dependencies
import kotlin.script.experimental.api.dependenciesSources
import kotlin.script.experimental.api.ide
import kotlin.script.experimental.jvm.impl.toClassPathOrEmpty
import kotlin.script.experimental.jvm.jdkHome
import kotlin.script.experimental.jvm.jvm
import kotlin.script.experimental.util.PropertiesCollection

fun indexSourceRootsEagerly(): Boolean = Registry.`is`("kotlin.scripting.index.dependencies.sources", false)

val KtFile.alwaysVirtualFile: VirtualFile get() = originalFile.virtualFile ?: viewProvider.virtualFile

inline fun <reified T : ScriptDefinitionsSource> Project.scriptDefinitionsSourceOfType(): T? =
    SCRIPT_DEFINITIONS_SOURCES.getExtensions(this).filterIsInstance<T>().firstOrNull()

val SCRIPT_DEFINITIONS_SOURCES: ProjectExtensionPointName<ScriptDefinitionsSource> =
    ProjectExtensionPointName("org.jetbrains.kotlin.scriptDefinitionsSource")

@set:TestOnly
var Application.isScriptChangesNotifierDisabled: Boolean by NotNullableUserDataProperty(
    Key.create("SCRIPT_CHANGES_NOTIFIER_DISABLED"), true
)

fun getAllDefinitions(project: Project): List<ScriptDefinition> = IdeScriptDefinitionProvider.getInstance(project).getDefinitions()

fun loggingReporter(severity: ScriptDiagnostic.Severity, message: String) {
    val log = Logger.getInstance("ScriptDefinitionsProviders")
    when (severity) {
        ScriptDiagnostic.Severity.FATAL, ScriptDiagnostic.Severity.ERROR -> log.error(message)

        ScriptDiagnostic.Severity.WARNING, ScriptDiagnostic.Severity.INFO -> log.info(message)

        else -> {}
    }
}


class NewScriptFileInfo(
    var id: String = "", var title: String = "", var templateName: String = "Kotlin Script", var icon: Icon = KotlinIcons.SCRIPT
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as NewScriptFileInfo

        return id == other.id
    }

    override fun hashCode(): Int {
        return id.hashCode()
    }
}

val IdeScriptCompilationConfigurationKeys.kotlinScriptTemplateInfo: PropertiesCollection.Key<NewScriptFileInfo> by PropertiesCollection.key()


/**
 * For using in DefaultScriptConfigurationManager and in tests only
 */
fun areSimilar(old: ScriptCompilationConfigurationWrapper, new: ScriptCompilationConfigurationWrapper): Boolean {
    if (old.script != new.script) return false

    val oldConfig = old.configuration
    val newConfig = new.configuration

    if (oldConfig == newConfig) return true
    if (oldConfig == null || newConfig == null) return false

    if (oldConfig[ScriptCompilationConfiguration.jvm.jdkHome] != newConfig[ScriptCompilationConfiguration.jvm.jdkHome]) return false

    // there is differences how script definition classpath is added to script classpath in old and new scripting API,
    // so it's important to compare the resulting classpath list, not only the value of key
    if (oldConfig[ScriptCompilationConfiguration.dependencies].toClassPathOrEmpty() != newConfig[ScriptCompilationConfiguration.dependencies].toClassPathOrEmpty()) return false

    if (oldConfig[ScriptCompilationConfiguration.ide.dependenciesSources] != newConfig[ScriptCompilationConfiguration.ide.dependenciesSources]) return false
    if (oldConfig[ScriptCompilationConfiguration.defaultImports] != newConfig[ScriptCompilationConfiguration.defaultImports]) return false

    return true
}

fun Project.getKtFile(virtualFile: VirtualFile?, ktFile: KtFile? = null): KtFile? {
    if (virtualFile == null) return null
    if (ktFile != null) {
        check(ktFile.originalFile.virtualFile == virtualFile)
        return ktFile
    } else {
        return runReadAction { PsiManager.getInstance(this).findFile(virtualFile) as? KtFile }
    }
}