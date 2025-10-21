// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.gradleTooling

import com.intellij.gradle.toolingExtension.util.GradleReflectionUtil
import com.intellij.gradle.toolingExtension.util.GradleVersionUtil
import org.gradle.api.Project
import org.gradle.api.artifacts.component.BuildIdentifier
import org.gradle.api.internal.project.ProjectInternal
import org.gradle.api.logging.Logger
import org.gradle.api.logging.Logging
import org.gradle.internal.build.BuildState
import org.jetbrains.plugins.gradle.tooling.AbstractModelBuilderService
import org.jetbrains.plugins.gradle.tooling.Message
import org.jetbrains.plugins.gradle.tooling.ModelBuilderContext
import java.io.Serializable

interface PrepareKotlinIdeImportTaskModel : Serializable {
    val prepareKotlinIdeaImportTaskNames: Set<String>
    val legacyTaskNames: Set<String>


    data class Impl(
        override val prepareKotlinIdeaImportTaskNames: Set<String>,
        override val legacyTaskNames: Set<String> = emptySet()
    ) : PrepareKotlinIdeImportTaskModel
}

class PrepareKotlinIdeaImportTaskModelBuilder : AbstractModelBuilderService() {

    override fun canBuild(modelName: String?): Boolean {
        return PrepareKotlinIdeImportTaskModel::class.java.name == modelName
    }

    override fun buildAll(modelName: String, project: Project, context: ModelBuilderContext): PrepareKotlinIdeImportTaskModel? {
        val prepareKotlinIdeaImportTaskNames = project.tasks.names
            .filter { taskName -> taskName.startsWith(TaskNames.prepareKotlinIdeaImport) }
            .toSet()

        if (prepareKotlinIdeaImportTaskNames.isNotEmpty()) {
            logger.debug(Messages.prepareKotlinIdeaTaskFound(project.path))
            project.addTasksToStartParameter(prepareKotlinIdeaImportTaskNames)
            return PrepareKotlinIdeImportTaskModel.Impl(prepareKotlinIdeaImportTaskNames)
        }

        /* Support for older KGP versions */
        else {
            logger.debug(Messages.prepareKotlinIdeaTaskNotFound(project.path))
            val legacyTaskNames = project.findPrepareImportTasksForOlderGradlePlugins()
            if (legacyTaskNames.isNotEmpty()) {
                logger.debug(Messages.oldPrepareKotlinIdeaImportTasksFound(project.path, legacyTaskNames))
                project.addTasksToStartParameter(legacyTaskNames)
                return PrepareKotlinIdeImportTaskModel.Impl(emptySet(), legacyTaskNames = legacyTaskNames)
            }
        }

        return null
    }

    /**
     * KGP < 1.7 will not support the 'prepareKotlinIdeaImport' and relies on 'runCommonizer' and 'podImport' tasks to be
     * enqueued explicitly by the IDE
     */
    private fun Project.findPrepareImportTasksForOlderGradlePlugins(): Set<String> {
        return listOfNotNull(findRegisteredPodImportTask(), findRunCommonizerTask()).toSet()
    }

    private fun Project.findRegisteredPodImportTask(): String? {
        return if (TaskNames.podImport in tasks.names) return TaskNames.podImport else null
    }

    private fun Project.findRunCommonizerTask(): String? {
        val kotlinExtension = project.extensions.findByName("kotlin") ?: return null
        val classLoader = kotlinExtension.javaClass.classLoader

        try {
            return if (KotlinNativePlatformDependenciesKt.invokeIsAllowCommonizerOrThrow(project, classLoader))
                TaskNames.runCommonizer else null

        } catch (t: Throwable) {
            PrepareKotlinIdeaImportTaskModelBuilder.logger.debug(Messages.failedEvaluatingIsAllowCommonizer, t)
            return null
        }
    }

    private fun Project.addTasksToStartParameter(taskNames: Iterable<String>) {
        if (project.gradle.parent == null) {
            /* Root of composite build: We can just add the task name */
            project.gradle.startParameter.setTaskNames(project.gradle.startParameter.taskNames.toSet() + taskNames)
        } else {
            /* This is an included build. Referencing the task path explicitly */
            val rootBuild = generateSequence(project.gradle) { it.parent }.last()
            val buildId = (project as ProjectInternal).services.get(BuildState::class.java).buildIdentifier
            val projectPathPart = if (rootProject != project) project.path else ""
            val absoluteTaskPaths = taskNames.map { taskName -> "${buildId.buildPathCompat}$projectPathPart:$taskName" }
            rootBuild.startParameter.setTaskNames(rootBuild.startParameter.taskNames.toSet() + absoluteTaskPaths)
        }
    }

    override fun reportErrorMessage(modelName: String, project: Project, context: ModelBuilderContext, exception: Exception) {
        context.messageReporter.createMessage()
            .withGroup(this)
            .withKind(Message.Kind.WARNING)
            .withTitle("prepareKotlinIdeImport")
            .withText(Messages.unknownFailure)
            .withException(exception)
            .reportMessage(project)
    }

    companion object {
        private val logger: Logger = Logging.getLogger(PrepareKotlinIdeaImportTaskModelBuilder::class.java)
    }

    object Messages {
        fun prepareKotlinIdeaTaskFound(projectPath: String) =
            "${TaskNames.prepareKotlinIdeaImport} task found in project $projectPath"

        fun prepareKotlinIdeaTaskNotFound(projectPath: String) =
            "${TaskNames.prepareKotlinIdeaImport} task not found in project $projectPath"

        fun oldPrepareKotlinIdeaImportTasksFound(projectPath: String, tasks: Iterable<String>): String =
            "KGP < 1.7 tasks found in project $projectPath ${tasks.distinct().sorted().joinToString(", ", "[", "]")}"

        const val failedEvaluatingIsAllowCommonizer =
            "Failed to evaluate ${KotlinNativePlatformDependenciesKt.isAllowCommonizerMethodName}"

        const val unknownFailure = "Failed to find ${TaskNames.prepareKotlinIdeaImport} task"
    }

    private object TaskNames {
        const val prepareKotlinIdeaImport = "prepareKotlinIdeaImport"
        const val runCommonizer = "runCommonizer"
        const val podImport = "podImport"
    }

    private object KotlinNativePlatformDependenciesKt {
        const val className = "org.jetbrains.kotlin.gradle.targets.native.internal.KotlinNativePlatformDependenciesKt"
        const val isAllowCommonizerMethodName = "isAllowCommonizer"

        fun invokeIsAllowCommonizerOrThrow(project: Project, classLoader: ClassLoader): Boolean {
            return Class.forName(className, false, classLoader)
                .getMethod(isAllowCommonizerMethodName, Project::class.java)
                .invoke(Boolean::class.java, project) as Boolean
        }
    }

    /**
     * Will return [BuildIdentifier.getBuildPath] for Gradle versions higher than 8.2
     * Will calculate the build path from the previously accessible [BuildIdentifier.getName]:
     * Note, this calculation will not be correct for nested composite builds!
     */
    private val BuildIdentifier.buildPathCompat: String
        get() {
            return if (GradleVersionUtil.isCurrentGradleAtLeast("8.2")) {
                buildPath
            } else {
                val name = GradleReflectionUtil.getValue(this@buildPathCompat, "getName", String::class.java)
                return if (name.startsWith(":")) {
                    name
                } else {
                    ":$name"
                }
            }
        }
}
