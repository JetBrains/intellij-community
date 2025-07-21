// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.gradle.scripting.k2

import com.intellij.openapi.components.*
import com.intellij.openapi.project.Project
import com.intellij.util.xmlb.annotations.Attribute
import org.jetbrains.kotlin.gradle.scripting.shared.GradleDefinitionsParams
import org.jetbrains.kotlin.gradle.scripting.shared.GradleScriptDefinitionWrapper
import org.jetbrains.kotlin.gradle.scripting.shared.loadGradleDefinitions
import org.jetbrains.kotlin.idea.core.script.k2.configurations.configurationResolverDelegate
import org.jetbrains.kotlin.idea.core.script.k2.configurations.scriptWorkspaceModelManagerDelegate
import org.jetbrains.kotlin.idea.core.script.k2.definitions.ScriptDefinitionProviderImpl
import org.jetbrains.kotlin.idea.core.script.shared.NewScriptFileInfo
import org.jetbrains.kotlin.idea.core.script.shared.kotlinScriptTemplateInfo
import org.jetbrains.kotlin.scripting.definitions.ScriptDefinition
import org.jetbrains.kotlin.scripting.definitions.ScriptDefinitionsSource
import java.util.concurrent.atomic.AtomicReference
import kotlin.script.experimental.api.ide

class GradleScriptDefinitionsSource(val project: Project) : ScriptDefinitionsSource {
    override val definitions: Sequence<ScriptDefinition>
        get() = GradleScriptDefinitionsStorage.getInstance(project).getDefinitions()
}

@Service(Service.Level.PROJECT)
@State(name = "GradleScriptDefinitionsStorage", storages = [Storage(StoragePathMacros.WORKSPACE_FILE)])
class GradleScriptDefinitionsStorage(val project: Project) :
    SerializablePersistentStateComponent<GradleScriptDefinitionsStorage.State>(State()) {

    private val _definitions: AtomicReference<List<ScriptDefinition>> = AtomicReference(listOf())
    private val initialized = AtomicReference(false)

    fun getDefinitions(): Sequence<ScriptDefinition> {
        initializeIfNeeded()
        return _definitions.get()?.asSequence() ?: emptySequence()
    }

    private fun initializeIfNeeded() {
        if (initialized.get()) return

        synchronized(this) {
            if (initialized.get()) return

            val params = state.toParams()
            if (params != null) {
                loadDefinitions(params)
            }
            initialized.set(true)
        }
    }

    fun loadDefinitions(params: GradleDefinitionsParams) {
        _definitions.set(loadAndWrapDefinitions(params))
        initialized.set(true)
        updateState {
            it.copyFrom(params)
        }
        ScriptDefinitionProviderImpl.getInstance(project).notifyDefinitionsChanged()
    }

    private fun loadAndWrapDefinitions(params: GradleDefinitionsParams): List<ScriptDefinition> {
        val definitions = loadGradleDefinitions(project, params)
        return definitions.map {
            if (it !is GradleScriptDefinitionWrapper) it
            else {
                it.with {
                    ide {
                        kotlinScriptTemplateInfo(NewScriptFileInfo().apply {
                            id = "gradle-kts"
                            title = ".gradle.kts"
                            templateName = "Kotlin Script Gradle"
                        })
                        configurationResolverDelegate {
                            GradleScriptRefinedConfigurationProvider.getInstance(project)
                        }
                        scriptWorkspaceModelManagerDelegate {
                            GradleScriptRefinedConfigurationProvider.getInstance(project)
                        }
                    }
                }
            }
        }
    }


    data class State(
        @Attribute @JvmField val workingDir: String? = null,
        @Attribute @JvmField val gradleHome: String? = null,
        @Attribute @JvmField val javaHome: String? = null,
        @Attribute @JvmField val gradleVersion: String? = null,
        @JvmField val jvmArguments: List<String> = emptyList(),
        @JvmField val environment: Map<String, String> = emptyMap(),
    ) {
        fun copyFrom(params: GradleDefinitionsParams): State {
            return copy(
                workingDir = params.workingDir,
                gradleHome = params.gradleHome,
                javaHome = params.javaHome,
                gradleVersion = params.gradleVersion,
                jvmArguments = params.jvmArguments,
                environment = params.environment
            )
        }

        fun toParams(): GradleDefinitionsParams? {
            return GradleDefinitionsParams(
                workingDir ?: return null,
                gradleHome ?: return null,
                javaHome ?: return null,
                gradleVersion ?: return null,
                jvmArguments ?: return null,
                environment ?: return null
            )
        }
    }

    companion object {
        fun getInstance(project: Project): GradleScriptDefinitionsStorage = project.service<GradleScriptDefinitionsStorage>()
    }
}
