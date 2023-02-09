// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.gradleTooling

import org.gradle.api.Project
import org.gradle.tooling.model.kotlin.dsl.KotlinDslModelsParameters.PREPARATION_TASK_NAME
import org.gradle.util.GradleVersion
import org.jetbrains.plugins.gradle.tooling.AbstractModelBuilderService
import org.jetbrains.plugins.gradle.tooling.ErrorMessageBuilder
import org.jetbrains.plugins.gradle.tooling.ModelBuilderContext
import java.lang.management.ManagementFactory
import javax.management.MBeanServer
import javax.management.ObjectName

class KotlinDslScriptTaskModelBuilder : AbstractModelBuilderService() {
    override fun canBuild(modelName: String): Boolean {
        return KotlinDslScriptAdditionalTask::class.java.name == modelName
    }

    private fun reportFUSMetricByJMX(method: String, type: String, metricName: String, value: Any, subprojectName: String?, weight: Long?) {
        val name = "org.jetbrains.kotlin.gradle.plugin.statistics:type=StatsService"
        val beanName = ObjectName(name)
        val mbs: MBeanServer = ManagementFactory.getPlatformMBeanServer()
         mbs.invoke(
                beanName,
                method,
                arrayOf(metricName, value, subprojectName, weight),
                arrayOf("java.lang.String", type, "java.lang.String", "java.lang.Long")
            )
    }

    override fun buildAll(modelName: String, project: Project, context: ModelBuilderContext): Any? {
        if (kotlinDslScriptsModelImportSupported(project.gradle.gradleVersion)) {
            val startParameter = project.gradle.startParameter

            val tasks = HashSet(startParameter.taskNames)
            tasks.add(PREPARATION_TASK_NAME)
            startParameter.setTaskNames(tasks)
            reportFUSMetricByJMX("reportBoolean", "boolean", "BUILD_PREPARE_KOTLIN_BUILD_SCRIPT_MODEL", true, null, null)
        }
        return null
    }

    override fun getErrorMessageBuilder(project: Project, e: Exception): ErrorMessageBuilder {
        return ErrorMessageBuilder.create(
            project, e, "Kotlin DSL script model errors"
        ).withDescription("Unable to set $PREPARATION_TASK_NAME sync task.")
    }

    private fun kotlinDslScriptsModelImportSupported(currentGradleVersion: String): Boolean {
        return GradleVersion.version(currentGradleVersion) >= GradleVersion.version("6.0")
    }

}