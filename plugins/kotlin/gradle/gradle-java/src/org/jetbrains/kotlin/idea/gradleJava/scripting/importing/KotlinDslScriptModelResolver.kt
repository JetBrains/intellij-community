// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.gradleJava.scripting.importing

import com.intellij.gradle.toolingExtension.modelAction.GradleModelFetchPhase
import com.intellij.gradle.toolingExtension.modelProvider.GradleClassBuildModelProvider
import com.intellij.openapi.progress.blockingContext
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.platform.workspace.storage.MutableEntityStorage
import org.gradle.tooling.model.kotlin.dsl.KotlinDslScriptsModel
import org.jetbrains.kotlin.idea.base.plugin.KotlinPluginModeProvider
import org.jetbrains.kotlin.idea.core.script.scriptConfigurationsSourceOfType
import org.jetbrains.kotlin.idea.core.script.scriptDefinitionsSourceOfType
import org.jetbrains.kotlin.idea.gradle.scripting.importing.KotlinDslScriptModelResolverCommon
import org.jetbrains.kotlin.idea.gradleJava.loadGradleDefinitions
import org.jetbrains.kotlin.idea.gradleJava.scripting.GradleScriptConfigurationsSource
import org.jetbrains.kotlin.idea.gradleJava.scripting.GradleScriptDefinitionsSource
import org.jetbrains.kotlin.idea.gradleJava.scripting.GradleScriptModel
import org.jetbrains.kotlin.idea.gradleJava.scripting.kotlinDslScriptsModelImportSupported
import org.jetbrains.kotlin.idea.gradleTooling.KotlinDslScriptAdditionalTask
import org.jetbrains.kotlin.idea.gradleTooling.KotlinDslScriptModelProvider
import org.jetbrains.kotlin.tooling.core.KotlinToolingVersion
import org.jetbrains.plugins.gradle.service.project.ProjectResolverContext
import org.jetbrains.plugins.gradle.service.syncAction.GradleSyncContributor
import org.jetbrains.plugins.gradle.service.syncAction.GradleSyncProjectConfigurator.project
import java.nio.file.Path

class KotlinDslScriptModelResolver : KotlinDslScriptModelResolverCommon() {

    override fun getModelProviders() = listOf(
        GradleClassBuildModelProvider(KotlinDslScriptAdditionalTask::class.java, GradleModelFetchPhase.PROJECT_LOADED_PHASE),
        KotlinDslScriptModelProvider()
    )

    override fun getExtraProjectModelClasses(): Set<Class<out Any>> {
        return mutableSetOf<Class<out Any>>(KotlinToolingVersion::class.java).also { it.addAll(super.getExtraProjectModelClasses())}
    }

    override fun getToolingExtensionsClasses(): Set<Class<out Any>> {
        return mutableSetOf<Class<out Any>>(KotlinToolingVersion::class.java).also { it.addAll(super.getToolingExtensionsClasses())}
    }
}

class KotlinDslScriptSyncContributor : GradleSyncContributor {

    override val name: String = "Kotlin DSL Script"

    override suspend fun onModelFetchCompleted(context: ProjectResolverContext, storage: MutableEntityStorage) {
        val project = context.project()
        val taskId = context.externalSystemTaskId
        val tasks = kotlinDslSyncListenerInstance?.tasks ?: return
        val sync = synchronized(tasks) { tasks[taskId] }

        blockingContext {
            for (buildModel in context.allBuilds) {
                for (projectModel in buildModel.projects) {
                    val projectIdentifier = projectModel.projectIdentifier.projectPath
                    if (projectIdentifier == ":") {
                        val gradleVersion = context.projectGradleVersion
                        if (gradleVersion != null && kotlinDslScriptsModelImportSupported(gradleVersion)) {
                            val model = context.getProjectModel(projectModel, KotlinDslScriptsModel::class.java)
                            if (model != null) {
                                if (!processScriptModel(context, sync, model, projectIdentifier)) {
                                    continue
                                }
                            }
                        }

                        saveGradleBuildEnvironment(context)
                    }
                }
            }
        }

        if (sync != null && KotlinPluginModeProvider.isK2Mode()) {
            val definitions = loadGradleDefinitions(sync.workingDir, sync.gradleHome, sync.javaHome, project)
            project.scriptDefinitionsSourceOfType<GradleScriptDefinitionsSource>()?.updateDefinitions(definitions)

            val gradleScripts = sync.models.mapNotNullTo(mutableSetOf()) {
                val path = Path.of(it.file)
                VirtualFileManager.getInstance().findFileByNioPath(path)?.let { virtualFile ->
                    GradleScriptModel(virtualFile, it.classPath, it.sourcePath, it.imports, sync.javaHome)
                }
            }

            project.scriptConfigurationsSourceOfType<GradleScriptConfigurationsSource>()
                ?.updateDependenciesAndCreateModules(gradleScripts, storage)
        }
    }
}
