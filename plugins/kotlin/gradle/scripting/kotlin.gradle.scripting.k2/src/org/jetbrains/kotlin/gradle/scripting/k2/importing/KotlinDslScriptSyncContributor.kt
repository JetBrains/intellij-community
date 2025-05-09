// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.gradle.scripting.k2.importing

import com.intellij.openapi.application.readAction
import com.intellij.openapi.progress.blockingContext
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.platform.workspace.storage.MutableEntityStorage
import com.intellij.psi.PsiManager
import org.gradle.tooling.model.kotlin.dsl.KotlinDslScriptsModel
import org.jetbrains.kotlin.gradle.scripting.k2.GradleScriptDefinitionsStorage
import org.jetbrains.kotlin.gradle.scripting.shared.GradleScriptModel
import org.jetbrains.kotlin.gradle.scripting.shared.GradleScriptRefinedConfigurationProvider
import org.jetbrains.kotlin.gradle.scripting.shared.importing.kotlinDslSyncListenerInstance
import org.jetbrains.kotlin.gradle.scripting.shared.importing.processScriptModel
import org.jetbrains.kotlin.gradle.scripting.shared.importing.saveGradleBuildEnvironment
import org.jetbrains.kotlin.gradle.scripting.shared.kotlinDslScriptsModelImportSupported
import org.jetbrains.kotlin.idea.core.script.k2.DefaultScriptResolutionStrategy
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.plugins.gradle.service.project.ProjectResolverContext
import org.jetbrains.plugins.gradle.service.syncAction.GradleSyncContributor
import org.jetbrains.plugins.gradle.service.syncAction.GradleSyncProjectConfigurator.project
import java.nio.file.Path

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

        if (sync == null || sync.models.isEmpty()) return

        GradleScriptDefinitionsStorage.getInstance(project).loadDefinitionsFromDisk(sync.workingDir, sync.gradleHome, sync.javaHome)
        val gradleScripts = sync.models.mapNotNullTo(mutableSetOf()) {
            val virtualFile = VirtualFileManager.getInstance().findFileByNioPath(Path.of(it.file)) ?: return@mapNotNullTo null
            GradleScriptModel(
                virtualFile,
                it.classPath,
                it.sourcePath,
                it.imports,
                sync.javaHome
            )
        }

        GradleScriptRefinedConfigurationProvider.getInstance(project).processScripts(gradleScripts, storage)

        val ktFiles = gradleScripts.mapNotNull {
            readAction { PsiManager.getInstance(project).findFile(it.virtualFile) as? KtFile }
        }.toTypedArray()

        DefaultScriptResolutionStrategy.getInstance(project).execute(*ktFiles).join()
    }
}