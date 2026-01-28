// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.gradle.scripting.k2.importing

import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.platform.eel.provider.getEelDescriptor
import com.intellij.platform.eel.provider.utils.asNio
import com.intellij.platform.workspace.storage.ImmutableEntityStorage
import com.intellij.platform.workspace.storage.toBuilder
import org.jetbrains.kotlin.gradle.scripting.k2.GradleKotlinScriptEntityProvider
import org.jetbrains.kotlin.gradle.scripting.k2.definition.withIdeKeys
import org.jetbrains.kotlin.gradle.scripting.k2.workspaceModel.GradleKotlinScriptEntitySource
import org.jetbrains.kotlin.gradle.scripting.shared.definition.GradleDefinitionsParams
import org.jetbrains.kotlin.gradle.scripting.shared.definition.loadGradleDefinitions
import org.jetbrains.kotlin.gradle.scripting.shared.importing.getKotlinDslScripts
import org.jetbrains.kotlin.gradle.scripting.shared.importing.reportErrors
import org.jetbrains.plugins.gradle.model.GradleBuildScriptClasspathModel
import org.jetbrains.plugins.gradle.service.project.ProjectResolverContext
import org.jetbrains.plugins.gradle.service.syncAction.GradleSyncContributor
import org.jetbrains.plugins.gradle.service.syncAction.GradleSyncPhase
import java.nio.file.Path
import kotlin.io.path.pathString

internal class KotlinDslScriptSyncContributor : GradleSyncContributor {
    override val name: String = "Kotlin DSL Script"

    override val phase: GradleSyncPhase = GradleSyncPhase.SCRIPT_MODEL_PHASE

    override suspend fun createProjectModel(
        context: ProjectResolverContext, storage: ImmutableEntityStorage
    ): ImmutableEntityStorage {
        val project = context.project

        val models = getKotlinDslScripts(context)

        if (models.isEmpty()) return storage

        val gradleHome = context.allBuilds.asSequence().flatMap { it.projects.asSequence() }
            .mapNotNull { context.getProjectModel(it, GradleBuildScriptClasspathModel::class.java) }
            .firstNotNullOfOrNull { it.gradleHomeDir?.absolutePath } ?: context.settings.gradleHome ?: return storage

        // String is then converted to `nio.Path` and must reside on the same eel as project
        // i.e: homePath = "/foo/java", eel is Docker, so javaHome must be "\\docker\..\foo\java\" to be converted to nioPath
        val javaHome = context.buildEnvironment.java.javaHome.asNio(context.project.getEelDescriptor())

        val builder = storage.toBuilder()

        // See `org.jetbrains.kotlin.gradle.scripting.k2.importing.KotlinDslBaseScriptSyncContributor`
        // See `org.jetbrains.kotlin.gradle.scripting.k2.importing.KotlinDslScriptSyncExtension`
        builder.replaceBySource(gradleKotlinScriptEntitySource(context), ImmutableEntityStorage.empty())

        val gradleScripts = models.mapNotNullTo(mutableSetOf()) {
            val virtualFile = VirtualFileManager.getInstance().findFileByNioPath(Path.of(it.file)) ?: return@mapNotNullTo null
            GradleScriptModel(
                virtualFile,
                it.classPath,
                it.sourcePath,
                it.imports,
                it.classpathModel
            )
        }

        val gradleDefinitionsParams = GradleDefinitionsParams(
            context.projectPath,
            gradleHome,
            javaHome.pathString,
            context.buildEnvironment.gradle.gradleVersion,
            context.settings.jvmArguments,
            context.settings.env
        )
        val definitions = loadGradleDefinitions(gradleDefinitionsParams).map { it.withIdeKeys() }

        val entitySource = GradleKotlinDslScriptEntitySource(context.projectPath, phase)
        val updatedStorage = GradleKotlinScriptEntityProvider.getInstance(project)
            .getUpdatedStorage(builder, entitySource, gradleScripts, definitions, javaHome.pathString)
        reportErrors(project, context.externalSystemTaskId, models)
        return updatedStorage
    }
}

class GradleScriptModel(
    val virtualFile: VirtualFile,
    val classPath: List<String>,
    val sourcePath: List<String>,
    val imports: List<String>,
    val classpathModel: GradleBuildScriptClasspathModel? = null,
)

private data class GradleKotlinDslScriptEntitySource(
    override val projectPath: String,
    override val phase: GradleSyncPhase,
) : GradleKotlinScriptEntitySource
