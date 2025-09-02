// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.gradleJava.execution

import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.openapi.externalSystem.model.project.ProjectData
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil
import com.intellij.openapi.externalSystem.util.ExternalSystemUtil
import com.intellij.task.ExecuteRunConfigurationTask
import org.jetbrains.kotlin.idea.run.KotlinRunConfiguration
import org.jetbrains.plugins.gradle.execution.build.GradleBaseApplicationEnvironmentProvider
import org.jetbrains.plugins.gradle.execution.build.GradleInitScriptParameters
import org.jetbrains.plugins.gradle.service.project.GradleProjectResolverUtil
import org.jetbrains.plugins.gradle.util.GradleConstants

/**
 * This provider is responsible for building [ExecutionEnvironment] for Kotlin JVM modules to be run using Gradle.
 */
class KotlinGradleAppEnvProvider : GradleBaseApplicationEnvironmentProvider<KotlinRunConfiguration>() {

    override fun isApplicable(task: ExecuteRunConfigurationTask): Boolean {
        return task.runProfile is KotlinRunConfiguration
    }

    override fun generateInitScript(params: GradleInitScriptParameters): String? {
        return org.jetbrains.kotlin.idea.gradleJava.execution.generateInitScript(params)
    }
}

internal fun generateInitScript(params: GradleInitScriptParameters): String? {
    // Init script creates the run task only for the project matching 'applicationConfiguration'.
    // To find the proper one we compare identifiers: the one provided by Gradle and the one we generate based on our project import
    // data (external module data).

    val extProjectPath = ExternalSystemApiUtil.getExternalProjectPath(params.module) ?: return null
    val extProjectInfo =
        ExternalSystemUtil.getExternalProjectInfo(params.module.project, GradleConstants.SYSTEM_ID, extProjectPath) ?: return null
    val extModuleData = GradleProjectResolverUtil.findModule(extProjectInfo.externalProjectStructure, extProjectPath) ?: return null

    // Pair of 'project.rootProject.name' and 'project.path' is a unique project id in Gradle (including composite builds).
    // So our one is built using the same principle.
    val projectPath = extModuleData.data.id
    val gradleProjectId = if (projectPath.startsWith(':')) { // shortening (is unique project id)
        val rootProjectName = (extModuleData.parent?.data as? ProjectData)?.externalName ?: ""
        rootProjectName + projectPath
    } else {
        projectPath.takeIf { ':' in it } ?: "$projectPath:" // includes rootProject.name already, for top level projects has no ':'
    }

    // @formatter:off
    // @Language("Groovy")
    val initScript = """
    def gradleProjectId = '$gradleProjectId'
    def runAppTaskName = '${params.runAppTaskName}'
    def mainClassToRun = '${params.mainClass}'
    def javaExePath = '${params.javaExePath}'
    def _workingDir = ${if (params.workingDirectory.isNullOrEmpty()) "null\n" else "'${params.workingDirectory}'\n"}
    def sourceSetName = '${params.sourceSetName}'
    
    def isOlderThan64 = GradleVersion.current().getBaseVersion().compareTo(GradleVersion.version("6.4")) < 0

    allprojects {
        afterEvaluate { project ->
            if (project.rootProject.name + project.path == gradleProjectId) {
                def overwrite = project.tasks.findByName(runAppTaskName) != null
                project.tasks.create(name: runAppTaskName, overwrite: overwrite, type: JavaExec) {
                    if (javaExePath) executable = javaExePath
                    if (project.pluginManager.hasPlugin("org.jetbrains.kotlin.multiplatform")) {
                        project.kotlin.targets.each { target ->
                            target.compilations.each { compilation ->
                                if (compilation.defaultSourceSetName == sourceSetName) {
                                    classpath = compilation.output.allOutputs + compilation.runtimeDependencyFiles
                                }
                            }
                        }
                    } else {
                        classpath = project.sourceSets[sourceSetName].runtimeClasspath
                    }
                    
                    if (isOlderThan64) {
                        main = mainClassToRun
                    } else {
                        mainClass = mainClassToRun
                    }
    
                    ${params.params}
                    if(_workingDir) workingDir = _workingDir
                    standardInput = System.in
                }
            }
        }
    }
    """
    // @formatter:on
    return initScript
}