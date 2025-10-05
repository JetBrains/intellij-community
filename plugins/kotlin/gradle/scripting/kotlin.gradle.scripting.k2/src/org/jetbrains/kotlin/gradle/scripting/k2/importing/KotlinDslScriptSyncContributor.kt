// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.gradle.scripting.k2.importing

import com.intellij.openapi.application.readAction
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.platform.workspace.storage.ImmutableEntityStorage
import com.intellij.platform.workspace.storage.MutableEntityStorage
import com.intellij.platform.workspace.storage.toBuilder
import com.intellij.psi.PsiManager
import org.jetbrains.kotlin.gradle.scripting.k2.GradleScriptDefinitionsStorage
import org.jetbrains.kotlin.gradle.scripting.k2.GradleScriptRefinedConfigurationProvider
import org.jetbrains.kotlin.gradle.scripting.shared.GradleDefinitionsParams
import org.jetbrains.kotlin.gradle.scripting.shared.GradleScriptModel
import org.jetbrains.kotlin.gradle.scripting.shared.GradleScriptModelData
import org.jetbrains.kotlin.gradle.scripting.shared.KotlinGradleScriptEntitySource
import org.jetbrains.kotlin.gradle.scripting.shared.importing.collectErrors
import org.jetbrains.kotlin.gradle.scripting.shared.importing.getKotlinDslScripts
import org.jetbrains.kotlin.gradle.scripting.shared.importing.kotlinDslSyncListenerInstance
import org.jetbrains.kotlin.gradle.scripting.shared.importing.saveGradleBuildEnvironment
import org.jetbrains.kotlin.idea.core.script.k2.highlighting.DefaultScriptResolutionStrategy
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.plugins.gradle.model.GradleBuildScriptClasspathModel
import org.jetbrains.plugins.gradle.service.project.ProjectResolverContext
import org.jetbrains.plugins.gradle.service.syncAction.GradleSyncContributor
import org.jetbrains.plugins.gradle.service.syncAction.GradleSyncExtension
import org.jetbrains.plugins.gradle.service.syncAction.GradleSyncPhase
import java.nio.file.Path

internal class KotlinDslScriptSyncExtension : GradleSyncExtension {

    override fun updateProjectModel(
        context: ProjectResolverContext, syncStorage: MutableEntityStorage, projectStorage: MutableEntityStorage, phase: GradleSyncPhase
    ) {
        if (phase == GradleSyncPhase.ADDITIONAL_MODEL_PHASE) {
            projectStorage.replaceBySource({ it is KotlinGradleScriptEntitySource }, syncStorage)
        }
    }
}

internal class KotlinDslScriptSyncContributor : GradleSyncContributor {

    override val name: String = "Kotlin DSL Script"

    override val phase: GradleSyncPhase = GradleSyncPhase.ADDITIONAL_MODEL_PHASE

    override suspend fun createProjectModel(
        context: ProjectResolverContext, storage: ImmutableEntityStorage
    ): ImmutableEntityStorage {
        val project = context.project
        val taskId = context.externalSystemTaskId
        val tasks = kotlinDslSyncListenerInstance?.tasks ?: return storage
        val sync = synchronized(tasks) { tasks[taskId] }

        val models = getKotlinDslScripts(context).toList()

        if (sync != null) {
            synchronized(sync) {
                sync.models.addAll(models)
                if (models.collectErrors().any()) {
                    sync.failed = true
                }
            }
        }

        saveGradleBuildEnvironment(context)

        if (models.isEmpty()) return storage

        val gradleHome = context.allBuilds.asSequence().flatMap { it.projects.asSequence() }
            .mapNotNull { context.getProjectModel(it, GradleBuildScriptClasspathModel::class.java) }
            .mapNotNull { it.gradleHomeDir?.absolutePath }.firstOrNull() ?: context.settings.gradleHome ?: return storage

        val javaHome = context.buildEnvironment.java.javaHome.absolutePath

        GradleScriptDefinitionsStorage.getInstance(project).loadDefinitions(
            params = GradleDefinitionsParams(
                context.projectPath,
                gradleHome,
                javaHome,
                context.buildEnvironment.gradle.gradleVersion,
                context.settings.jvmArguments,
                context.settings.env
            )
        )

        val gradleScripts = models.mapNotNullTo(mutableSetOf()) {
            val virtualFile = VirtualFileManager.getInstance().findFileByNioPath(Path.of(it.file)) ?: return@mapNotNullTo null
            GradleScriptModel(
                virtualFile,
                it.classPath,
                it.sourcePath,
                it.imports,
            )
        }

        val builder = storage.toBuilder()

        GradleScriptRefinedConfigurationProvider.getInstance(project)
            .processScripts(GradleScriptModelData(gradleScripts, javaHome), builder)

        val ktFiles = gradleScripts.mapNotNull {
            readAction { PsiManager.getInstance(project).findFile(it.virtualFile) as? KtFile }
        }.toTypedArray()

        DefaultScriptResolutionStrategy.getInstance(project).execute(*ktFiles).join()

        return builder.toSnapshot()
    }
}