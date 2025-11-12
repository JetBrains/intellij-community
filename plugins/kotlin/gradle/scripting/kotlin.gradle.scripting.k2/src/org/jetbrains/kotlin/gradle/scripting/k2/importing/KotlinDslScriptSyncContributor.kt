// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.gradle.scripting.k2.importing

import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.platform.eel.provider.getEelDescriptor
import com.intellij.platform.eel.provider.utils.asNio
import com.intellij.platform.workspace.storage.ImmutableEntityStorage
import com.intellij.platform.workspace.storage.toBuilder
import org.jetbrains.kotlin.gradle.scripting.k2.GradleKotlinScriptService
import org.jetbrains.kotlin.gradle.scripting.shared.definition.GradleDefinitionsParams
import org.jetbrains.kotlin.gradle.scripting.shared.importing.collectErrors
import org.jetbrains.kotlin.gradle.scripting.shared.importing.getKotlinDslScripts
import org.jetbrains.kotlin.gradle.scripting.shared.importing.kotlinDslSyncListenerInstance
import org.jetbrains.kotlin.gradle.scripting.shared.importing.saveGradleBuildEnvironment
import org.jetbrains.plugins.gradle.model.GradleBuildScriptClasspathModel
import org.jetbrains.plugins.gradle.service.project.ProjectResolverContext
import org.jetbrains.plugins.gradle.service.syncAction.GradleSyncContributor
import org.jetbrains.plugins.gradle.service.syncAction.GradleSyncPhase
import java.nio.file.Path
import kotlin.io.path.pathString

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
            .firstNotNullOfOrNull { it.gradleHomeDir?.absolutePath } ?: context.settings.gradleHome ?: return storage

        // String is then converted to `nio.Path` and must reside on the same eel as project
        // i.e: homePath = "/foo/java", eel is Docker, so javaHome must be "\\docker\..\foo\java\" to be converted to nioPath
        val javaHome = context.buildEnvironment.java.javaHome.asNio(context.project.getEelDescriptor())

        val builder = storage.toBuilder()

        val gradleScripts = models.mapNotNullTo(mutableSetOf()) {
            val virtualFile = VirtualFileManager.getInstance().findFileByNioPath(Path.of(it.file)) ?: return@mapNotNullTo null
            GradleScriptModel(
                virtualFile,
                it.classPath,
                it.sourcePath,
                it.imports,
            )
        }

        val scriptData = GradleScriptData(
            gradleScripts,
            GradleDefinitionsParams(
                context.projectPath,
                gradleHome,
                javaHome.pathString,
                context.buildEnvironment.gradle.gradleVersion,
                context.settings.jvmArguments,
                context.settings.env
            )
        )

        GradleKotlinScriptService.getInstance(project).updateStorage(scriptData, builder)

        return builder.toSnapshot()
    }
}

class GradleScriptData(
    val models: Collection<GradleScriptModel>,
    val definitionsParams: GradleDefinitionsParams
)

class GradleScriptModel(
    val virtualFile: VirtualFile,
    val classPath: List<String>,
    val sourcePath: List<String>,
    val imports: List<String>,
)