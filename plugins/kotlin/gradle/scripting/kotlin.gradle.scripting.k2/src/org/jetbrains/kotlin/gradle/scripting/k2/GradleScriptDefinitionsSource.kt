// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.gradle.scripting.k2

import com.intellij.openapi.components.*
import com.intellij.openapi.project.Project
import com.intellij.util.xmlb.annotations.Attribute
import org.jetbrains.kotlin.gradle.scripting.shared.GradleScriptDefinitionWrapper
import org.jetbrains.kotlin.gradle.scripting.shared.loadGradleDefinitions
import org.jetbrains.kotlin.idea.core.script.NewScriptFileInfo
import org.jetbrains.kotlin.idea.core.script.k2.configurations.configurationResolverDelegate
import org.jetbrains.kotlin.idea.core.script.k2.configurations.scriptWorkspaceModelManagerDelegate
import org.jetbrains.kotlin.idea.core.script.k2.definitions.ScriptDefinitionProviderImpl
import org.jetbrains.kotlin.idea.core.script.kotlinScriptTemplateInfo
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

    private val definitions: AtomicReference<List<ScriptDefinition>> = AtomicReference(listOf())
    private val initialized = AtomicReference(false)

    fun getDefinitions(): Sequence<ScriptDefinition> {
        initializeIfNeeded()
        return definitions.get()?.asSequence() ?: emptySequence()
    }

    private fun initializeIfNeeded() {
        if (initialized.get()) return

        synchronized(this) {
            if (!initialized.get()) {
                val state = state
                if (state.workingDir != null) {
                    loadAndWrapDefinitions(state.workingDir, state.gradleHome, state.javaHome, state.gradleVersion).let {
                        definitions.set(it)
                    }
                }

                initialized.set(true)
            }
        }
    }

    fun loadDefinitionsFromDisk(workingDir: String, gradleHome: String?, javaHome: String?, gradleVersion: String?) {
        definitions.set(loadAndWrapDefinitions(workingDir, gradleHome, javaHome, gradleVersion))
        updateState {
            it.copy(workingDir = workingDir, gradleHome = gradleHome, javaHome = javaHome, gradleVersion = gradleVersion)
        }
        ScriptDefinitionProviderImpl.getInstance(project).notifyDefinitionsChanged()
    }

    private fun loadAndWrapDefinitions(
        workingDir: String,
        gradleHome: String?,
        javaHome: String?,
        gradleVersion: String?
    ): List<ScriptDefinition> {
        val definitions = loadGradleDefinitions(
            workingDir = workingDir,
            gradleHome = gradleHome,
            javaHome = javaHome,
            gradleVersion = gradleVersion,
            project = project
        )
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
    )

    companion object {
        fun getInstance(project: Project): GradleScriptDefinitionsStorage = project.service<GradleScriptDefinitionsStorage>()
    }
}
