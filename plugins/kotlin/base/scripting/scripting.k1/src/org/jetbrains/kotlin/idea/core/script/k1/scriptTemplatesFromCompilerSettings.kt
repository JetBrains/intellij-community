// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.core.script.k1

import com.intellij.openapi.project.Project
import org.jetbrains.kotlin.config.CompilerSettings
import org.jetbrains.kotlin.idea.compiler.configuration.KotlinCompilerSettings
import org.jetbrains.kotlin.idea.compiler.configuration.KotlinCompilerSettingsListener
import org.jetbrains.kotlin.idea.core.KotlinPluginDisposable
import org.jetbrains.kotlin.idea.core.script.shared.definition.loadDefinitionsFromTemplatesByPaths
import org.jetbrains.kotlin.idea.util.application.executeOnPooledThread
import org.jetbrains.kotlin.scripting.definitions.ScriptDefinition
import org.jetbrains.kotlin.scripting.definitions.ScriptDefinitionsSource
import org.jetbrains.kotlin.scripting.definitions.getEnvironment
import java.io.File
import kotlin.io.path.Path
import kotlin.script.experimental.host.ScriptingHostConfiguration
import kotlin.script.experimental.jvm.defaultJvmScriptingHostConfiguration

class ScriptTemplatesFromCompilerSettingsProvider(
    private val project: Project
) : ScriptDefinitionsSource {

    init {
        project.messageBus.connect(KotlinPluginDisposable.getInstance(project))
            .subscribe(KotlinCompilerSettingsListener.TOPIC, object : KotlinCompilerSettingsListener {
                override fun <T> settingsChanged(oldSettings: T?, newSettings: T?) {
                    if (newSettings !is CompilerSettings) return

                    executeOnPooledThread {
                        if (project.isDefault || project.isDisposed) return@executeOnPooledThread
                        ScriptDefinitionsManager.getInstance(project).reloadDefinitionsBy(this@ScriptTemplatesFromCompilerSettingsProvider)
                    }
                }
            })
    }

    override val definitions: Sequence<ScriptDefinition>
        get() {
            if (project.isDisposed) return emptySequence()
            return KotlinCompilerSettings.getInstance(project).settings.let { kotlinSettings ->
                if (kotlinSettings.scriptTemplates.isBlank()) emptySequence()
                else loadDefinitionsFromTemplatesByPaths(
                    templateClassNames = kotlinSettings.scriptTemplates.split(',', ' '),
                    templateClasspath = kotlinSettings.scriptTemplatesClasspath.split(File.pathSeparator).map(::Path),
                    baseHostConfiguration = ScriptingHostConfiguration(defaultJvmScriptingHostConfiguration) {
                        getEnvironment {
                            mapOf(
                                "projectRoot" to (project.basePath ?: project.baseDir.canonicalPath)?.let(::File)
                            )
                        }
                    }
                ).asSequence()
            }
        }
}

