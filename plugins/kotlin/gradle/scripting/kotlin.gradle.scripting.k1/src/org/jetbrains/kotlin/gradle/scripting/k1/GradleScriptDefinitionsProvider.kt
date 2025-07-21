// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.gradle.scripting.k1

import KotlinGradleScriptingBundle
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil
import com.intellij.openapi.progress.runBlockingMaybeCancellable
import com.intellij.openapi.project.Project
import org.jetbrains.kotlin.gradle.scripting.shared.ErrorGradleScriptDefinition
import org.jetbrains.kotlin.gradle.scripting.shared.GradleDefinitionsParams
import org.jetbrains.kotlin.gradle.scripting.shared.roots.GradleBuildRootsLocator
import org.jetbrains.kotlin.gradle.scripting.shared.roots.Imported
import org.jetbrains.kotlin.gradle.scripting.shared.roots.WithoutScriptModels
import org.jetbrains.kotlin.idea.core.script.k1.ScriptDefinitionsManager
import org.jetbrains.kotlin.idea.core.script.shared.SCRIPT_DEFINITIONS_SOURCES
import org.jetbrains.kotlin.idea.core.script.v1.scriptingInfoLog
import org.jetbrains.kotlin.scripting.definitions.ScriptDefinition
import org.jetbrains.kotlin.scripting.definitions.ScriptDefinitionsSource
import org.jetbrains.kotlin.scripting.definitions.runReadAction
import org.jetbrains.plugins.gradle.settings.DistributionType
import org.jetbrains.plugins.gradle.settings.GradleExecutionSettings
import org.jetbrains.plugins.gradle.settings.GradleSettingsListener
import org.jetbrains.plugins.gradle.util.GradleConstants
import java.util.concurrent.ConcurrentHashMap

class GradleScriptDefinitionsContributor(private val project: Project) : ScriptDefinitionsSource {
    companion object {
        fun getInstance(project: Project): GradleScriptDefinitionsContributor? =
            SCRIPT_DEFINITIONS_SOURCES.getExtensions(project)
                .filterIsInstance<GradleScriptDefinitionsContributor>()
                .singleOrNull()

        fun getDefinitions(project: Project, workingDir: String, gradleHome: String?, javaHome: String?): List<ScriptDefinition>? {
            val contributor = getInstance(project)

            if (contributor == null) {
                scriptingInfoLog(
                    "cannot find gradle script definitions contributor in SCRIPT_DEFINITIONS_SOURCES list: " +
                            "workingDir=$workingDir gradleHome=$gradleHome"
                )
                return null
            }
            if (gradleHome == null) {
                scriptingInfoLog(KotlinGradleScriptingBundle.message("error.text.unable.to.get.gradle.home.directory") + ": workingDir=$workingDir")
                return null
            }

            val root = LightGradleBuildRoot(workingDir, gradleHome, javaHome)
            with(contributor) {
                if (root.isError()) return null
            }

            contributor.reloadIfNeeded(workingDir, gradleHome, javaHome)

            val definitions = contributor.definitionsByRoots[root]
            if (definitions == null) {
                scriptingInfoLog(
                    "script definitions aren't loaded yet. " +
                            "They should be loaded by invoking GradleScriptDefinitionsContributor.reloadIfNeeded from KotlinDslSyncListener: " +
                            "workingDir=$workingDir gradleHome=$gradleHome"
                )
                return null
            }
            return definitions
        }
    }

    init {
        subscribeToGradleSettingChanges()
    }

    internal data class LightGradleBuildRoot(val workingDir: String, val gradleHome: String?, val javaHome: String?)

    private val definitionsByRoots = ConcurrentHashMap<LightGradleBuildRoot, List<ScriptDefinition>>()

    private fun LightGradleBuildRoot.markAsError() {
        definitionsByRoots[this] = listOf(ErrorGradleScriptDefinition(project))
    }

    private fun LightGradleBuildRoot.isError(): Boolean {
        return definitionsByRoots[this]?.any { it is ErrorGradleScriptDefinition } ?: false
    }

    private fun forceReload() {
        for (key in definitionsByRoots.keys()) {
            key.markAsError()
        }
        ScriptDefinitionsManager.getInstance(project).reloadDefinitionsBy(this)
    }

    // TODO: remove old roots
    fun reloadIfNeeded(workingDir: String, gradleHome: String?, javaHome: String?) {
        val root = LightGradleBuildRoot(workingDir, gradleHome, javaHome)
        val value = definitionsByRoots[root]
        if (value != null) {
            if (root.isError()) {
                ScriptDefinitionsManager.getInstance(project).reloadDefinitionsBy(this)
            }
        } else {
            val rootWithChangedGradleHome = definitionsByRoots.filter { it.key.workingDir == workingDir }
            if (rootWithChangedGradleHome.isNotEmpty()) {
                rootWithChangedGradleHome.forEach {
                    definitionsByRoots.remove(it.key)
                }
            }
            root.markAsError()
            ScriptDefinitionsManager.getInstance(project).reloadDefinitionsBy(this)
        }
    }

    private fun loadGradleDefinitions(root: LightGradleBuildRoot): List<ScriptDefinition> {
        val settings = runReadAction {
            runBlockingMaybeCancellable {
                ExternalSystemApiUtil.getExecutionSettings<GradleExecutionSettings>(
                    project, root.workingDir, GradleConstants.SYSTEM_ID
                )
            }
        }

        return org.jetbrains.kotlin.gradle.scripting.shared.loadGradleDefinitions(
            project, GradleDefinitionsParams(
                workingDir = root.workingDir,
                gradleHome = root.gradleHome,
                javaHome = root.javaHome,
                gradleVersion = null,
                jvmArguments = settings.jvmArguments,
                environment = settings.env
            )
        )
    }

    private fun subscribeToGradleSettingChanges() {
        val listener = object : GradleSettingsListener {
            override fun onGradleVmOptionsChange(oldOptions: String?, newOptions: String?) {
                forceReload()
            }

            override fun onGradleHomeChange(oldPath: String?, newPath: String?, linkedProjectPath: String) {
                forceReload()
            }

            override fun onServiceDirectoryPathChange(oldPath: String?, newPath: String?) {
                forceReload()
            }

            override fun onGradleDistributionTypeChange(currentValue: DistributionType?, linkedProjectPath: String) {
                forceReload()
            }
        }
        project.messageBus.connect().subscribe(GradleSettingsListener.TOPIC, listener)
    }

    // NOTE: control flow here depends on suppressing exceptions from loadGradleTemplates calls
    // TODO: possibly combine exceptions from every loadGradleTemplates call, be mindful of KT-19276
    override val definitions: Sequence<ScriptDefinition>
        get() {
            definitionsByRoots.keys().iterator().forEachRemaining { root -> // reload definitions marked as error
                if (root.isError()) {
                    definitionsByRoots[root] = loadGradleDefinitions(root)
                }
            }
            if (definitionsByRoots.isEmpty()) { // can be empty in case when import wasn't done from IDE start up,
                // otherwise KotlinDslSyncListener should run reloadIfNeeded for valid roots
                for (it in GradleBuildRootsLocator.getInstance(project).getAllRoots()) {
                    val workingDir = it.pathPrefix
                    val (gradleHome, javaHome) = when (it) {
                        is Imported -> {
                            it.data.gradleHome to it.data.javaHome
                        }

                        is WithoutScriptModels -> {
                            val settings = runReadAction {
                                runBlockingMaybeCancellable {
                                    ExternalSystemApiUtil.getExecutionSettings<GradleExecutionSettings>(
                                        project, workingDir, GradleConstants.SYSTEM_ID
                                    )
                                }
                            } ?: continue

                            settings.gradleHome to settings.javaHome
                        }
                    }
                    val root = LightGradleBuildRoot(workingDir, gradleHome, javaHome)
                    definitionsByRoots[root] = loadGradleDefinitions(root)
                }
            }
            if (definitionsByRoots.isNotEmpty()) {
                return definitionsByRoots.flatMap { it.value }.asSequence()
            }
            return sequenceOf(ErrorGradleScriptDefinition(project))
        }
}
