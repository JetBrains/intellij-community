// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.gradle.scripting.importing

import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskId
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.util.io.FileUtil.toSystemIndependentName
import com.intellij.openapi.util.io.PathPrefixTree
import com.intellij.openapi.vfs.VfsUtil
import org.gradle.tooling.model.kotlin.dsl.EditorReportSeverity
import org.gradle.tooling.model.kotlin.dsl.KotlinDslScriptsModel
import org.jetbrains.kotlin.gradle.scripting.getGradleScriptInputsStamp
import org.jetbrains.kotlin.gradle.scripting.kotlinDslScriptsModelImportSupported
import org.jetbrains.plugins.gradle.model.GradleBuildScriptClasspathModel
import org.jetbrains.plugins.gradle.model.GradleLightBuild
import org.jetbrains.plugins.gradle.service.project.ProjectResolverContext
import java.io.File

fun getKotlinDslScripts(context: ProjectResolverContext): List<KotlinDslScriptModel> = buildList {
    if (kotlinDslScriptsModelImportSupported(context.projectGradleVersion)) {
        for (buildModel in context.allBuilds) {
            addAll(getDslScriptModels(context, buildModel))
        }
    }
}

@Suppress("IO_FILE_USAGE")
private fun getDslScriptModels(
    context: ProjectResolverContext,
    buildModel: GradleLightBuild
): List<KotlinDslScriptModel> {
    val scriptsModel = context.getBuildModel(buildModel, KotlinDslScriptsModel::class.java) ?: return emptyList()

    val classpathModelIndex = PathPrefixTree.createMap<GradleBuildScriptClasspathModel>()
    for (projectModel in buildModel.projects) {
        val classpathModel = context.getProjectModel(projectModel, GradleBuildScriptClasspathModel::class.java) ?: continue
        classpathModelIndex.put(projectModel.projectDirectory.toPath(), classpathModel)
    }

    return scriptsModel.scriptModels.mapNotNull { (file, model) ->
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
        val gradleScriptInputsStamp = getGradleScriptInputsStamp(context.project, virtualFile) ?: return@mapNotNull null
        KotlinDslScriptModel(
            toSystemIndependentName(file.path),
            gradleScriptInputsStamp,
            model.classPath.map { toSystemIndependentName(it.path) },
            model.sourcePath.map { toSystemIndependentName(it.path) },
            model.implicitImports,
            messages,
            classpathModelIndex.getAncestorValues(file.toPath()).lastOrNull()
        )
    }
}

fun reportErrors(
    project: Project,
    taskId: ExternalSystemTaskId,
    models: List<KotlinDslScriptModel>,
) {
    val errorReporter = KotlinGradleDslErrorReporter(project, taskId)

    models.forEach { model ->
        errorReporter.reportError(File(model.file), model)
    }
}
