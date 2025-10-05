// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.gradle.scripting.shared.importing

import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskId
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.util.io.FileUtil.toSystemIndependentName
import com.intellij.openapi.vfs.VfsUtil
import org.gradle.tooling.model.kotlin.dsl.EditorReportSeverity
import org.gradle.tooling.model.kotlin.dsl.KotlinDslScriptsModel
import org.jetbrains.kotlin.gradle.scripting.shared.getGradleScriptInputsStamp
import org.jetbrains.kotlin.gradle.scripting.shared.kotlinDslScriptsModelImportSupported
import org.jetbrains.kotlin.gradle.scripting.shared.roots.GradleBuildRootsLocator
import org.jetbrains.kotlin.idea.gradleTooling.BrokenKotlinDslScriptsModel
import org.jetbrains.plugins.gradle.model.GradleBuildScriptClasspathModel
import org.jetbrains.plugins.gradle.service.project.ProjectResolverContext
import java.io.File

fun saveGradleBuildEnvironment(resolverCtx: ProjectResolverContext) {
    val task = resolverCtx.externalSystemTaskId
    val tasks = kotlinDslSyncListenerInstance?.tasks ?: return
    synchronized(tasks) { tasks[task] }?.let { sync ->
        val gradleHome = resolverCtx.getRootModel(GradleBuildScriptClasspathModel::class.java)?.gradleHomeDir?.path
            ?: resolverCtx.settings.gradleHome

        synchronized(sync) {
            sync.gradleVersion = resolverCtx.projectGradleVersion
            sync.javaHome = toSystemIndependentName(resolverCtx.buildEnvironment.java.javaHome.path)

            if (gradleHome != null) {
                sync.gradleHome = toSystemIndependentName(gradleHome)
            }
        }
    }
}

fun getKotlinDslScripts(context: ProjectResolverContext): Sequence<KotlinDslScriptModel> = sequence {
    if (!kotlinDslScriptsModelImportSupported(context.projectGradleVersion)) return@sequence

    context.allBuilds.flatMap { it.projects }
        .asSequence()
        .filter { it.projectIdentifier.projectPath == ":" }
        .mapNotNull {
            context.getProjectModel(it, KotlinDslScriptsModel::class.java)
        }.forEach {
            if (it is BrokenKotlinDslScriptsModel) {
                LOG.error("Couldn't get KotlinDslScriptsModel: \n${it.message}\n${it.stackTrace}")
                return@sequence
            }

            yieldAll(it.toListOfScriptModels(context.project))
        }
}

fun Collection<KotlinDslScriptModel>.collectErrors(): List<KotlinDslScriptModel.Message> {
    return this.flatMap { it.messages.filter { msg -> msg.severity == KotlinDslScriptModel.Severity.ERROR } }
}

fun KotlinDslScriptsModel.toListOfScriptModels(project: Project): List<KotlinDslScriptModel> =
    scriptModels.mapNotNull { (file, model) ->
        val messages = mutableListOf<KotlinDslScriptModel.Message>()

        model.exceptions.forEach {
            val fromException = parsePositionFromException(it)
            if (fromException != null) {
                val (filePath, _) = fromException
                if (filePath != file.path) return@forEach
            }
            @NlsSafe val delimiter = System.lineSeparator()
            @Suppress("HardCodedStringLiteral")
            messages.add(
                KotlinDslScriptModel.Message(
                    KotlinDslScriptModel.Severity.ERROR,
                    it.substringBefore(delimiter),
                    it,
                    fromException?.second
                )
            )
        }

        model.editorReports.forEach {
            messages.add(
                KotlinDslScriptModel.Message(
                    when (it.severity) {
                        EditorReportSeverity.WARNING -> KotlinDslScriptModel.Severity.WARNING
                        else -> KotlinDslScriptModel.Severity.ERROR
                    },
                    it.message,
                    position = KotlinDslScriptModel.Position(it.position?.line ?: 0, it.position?.column ?: 0)
                )
            )
        }

        val virtualFile = VfsUtil.findFile(file.toPath(), true) ?: return@mapNotNull null

        // todo(KT-34440): take inputs snapshot before starting import
        val gradleScriptInputsStamp = getGradleScriptInputsStamp(project, virtualFile) ?: return@mapNotNull null
        KotlinDslScriptModel(
            toSystemIndependentName(file.path),
            gradleScriptInputsStamp,
            model.classPath.map { toSystemIndependentName(it.path) },
            model.sourcePath.map { toSystemIndependentName(it.path) },
            model.implicitImports,
            messages
        )
    }

class KotlinDslGradleBuildSync(val workingDir: String, val taskId: ExternalSystemTaskId) {
    val creationTimestamp: Long = System.currentTimeMillis()
    // TODO: projectId is inconsistent - see com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskId.getProjectId
    var projectId: String? = null
    var gradleVersion: String? = null
    var gradleHome: String? = null
    var javaHome: String? = null
    val projectRoots: MutableSet<String> = mutableSetOf()
    val models: MutableList<KotlinDslScriptModel> = mutableListOf()
    var failed: Boolean = false

    override fun toString(): String {
        return "KotlinGradleDslSync(workingDir=$workingDir, gradleVersion=$gradleVersion, gradleHome=$gradleHome, javaHome=$javaHome, projectRoots=$projectRoots, failed=$failed)"
    }
}

fun saveScriptModels(project: Project, build: KotlinDslGradleBuildSync) {
    synchronized(build) {
        reportErrors(project, build)

        // todo: use real info about projects
        build.projectRoots.addAll(build.models.map { toSystemIndependentName(File(it.file).parent) })

        GradleBuildRootsLocator.getInstance(project)?.update(build)
    }
}

internal fun reportErrors(
    project: Project,
    build: KotlinDslGradleBuildSync
) {
    synchronized(build) {
        val errorReporter = KotlinGradleDslErrorReporter(project, build.taskId)

        build.models.forEach { model ->
            errorReporter.reportError(File(model.file), model)
        }
    }
}
