// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.gradle.scripting.shared.importing

import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskId
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.util.io.FileUtil.toSystemIndependentName
import com.intellij.openapi.vfs.VfsUtil
import org.gradle.tooling.model.kotlin.dsl.EditorReportSeverity
import org.gradle.tooling.model.kotlin.dsl.KotlinDslScriptsModel
import org.jetbrains.kotlin.gradle.scripting.shared.getGradleScriptInputsStamp
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
            sync.javaHome = resolverCtx.buildEnvironment
                ?.java?.javaHome?.path
                ?.let { toSystemIndependentName(it) }

            if (gradleHome != null) {
                sync.gradleHome = toSystemIndependentName(gradleHome)
            }
        }
    }
}

fun processScriptModel(
    resolverCtx: ProjectResolverContext,
    sync: KotlinDslGradleBuildSync?,
    model: KotlinDslScriptsModel,
    projectName: String
): Boolean {
    return if (model is BrokenKotlinDslScriptsModel) {
        LOG.error(
            "Couldn't get KotlinDslScriptsModel for $projectName:\n${model.message}\n${model.stackTrace}"
        )
        false
    } else {
        val task = resolverCtx.externalSystemTaskId
        val project = task.findProject() ?: return false
        val models = model.toListOfScriptModels(project)

        if (sync != null) {
            synchronized(sync) {
                sync.models.addAll(models)
            }
        }

        val errors = models.collectErrors()
        if (errors.isNotEmpty()) {
            sync?.let {
                synchronized(it) {
                    it.failed = true
                }
            }
            throw ProcessCanceledException()
        }
        true
    }
}

private fun Collection<KotlinDslScriptModel>.collectErrors(): List<KotlinDslScriptModel.Message> {
    return this.flatMap { it.messages.filter { msg -> msg.severity == KotlinDslScriptModel.Severity.ERROR } }
}

private fun KotlinDslScriptsModel.toListOfScriptModels(project: Project): List<KotlinDslScriptModel> =
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
    val projectRoots = mutableSetOf<String>()
    val models = mutableListOf<KotlinDslScriptModel>()
    var failed = false

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
