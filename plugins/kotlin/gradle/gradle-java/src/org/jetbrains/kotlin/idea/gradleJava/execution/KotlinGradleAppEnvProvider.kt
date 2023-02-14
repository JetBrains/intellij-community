// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.gradleJava.execution

import com.intellij.execution.configurations.JavaParameters
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.execution.util.ProgramParametersUtil
import com.intellij.openapi.externalSystem.model.project.ProjectData
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil
import com.intellij.openapi.externalSystem.util.ExternalSystemUtil
import com.intellij.openapi.module.Module
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.util.registry.Registry
import com.intellij.psi.PsiClass
import com.intellij.task.ExecuteRunConfigurationTask
import org.jetbrains.kotlin.idea.run.KotlinRunConfiguration
import org.jetbrains.plugins.gradle.execution.build.GradleBaseApplicationEnvironmentProvider
import org.jetbrains.plugins.gradle.service.project.GradleProjectResolverUtil
import org.jetbrains.plugins.gradle.util.GradleConstants

/**
 * This provider is responsible for building [ExecutionEnvironment] for Kotlin JVM modules to be run using Gradle.
 */
class KotlinGradleAppEnvProvider : GradleBaseApplicationEnvironmentProvider<KotlinRunConfiguration>() {

    override fun isApplicable(task: ExecuteRunConfigurationTask): Boolean {
        val enabled = Registry.`is`("kotlin.gradle-run.enabled", false)
        return enabled && task.runProfile is KotlinRunConfiguration
    }

    override fun generateInitScript(
            applicationConfiguration: KotlinRunConfiguration, module: Module,
            params: JavaParameters, gradleTaskPath: String, runAppTaskName: String, mainClass: PsiClass, javaExePath: String,
            sourceSetName: String, javaModuleName: String?
        ): String? {
            // Init script creates the run task only for the project matching 'applicationConfiguration'.
            // To find the proper one we compare identifiers: the one provided by Gradle and the one we generate based on our project import
            // data (external module data).

            val extProjectPath = ExternalSystemApiUtil.getExternalProjectPath(module) ?: return null
            val extProjectInfo = ExternalSystemUtil.getExternalProjectInfo(module.project, GradleConstants.SYSTEM_ID, extProjectPath) ?: return null
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

            val workingDir = ProgramParametersUtil.getWorkingDir(applicationConfiguration, module.project, module)?.let {
                FileUtil.toSystemIndependentName(it)
            }

            // @formatter:off
            @Suppress("UnnecessaryVariable")
//      @Language("Groovy")
            val initScript = """
    def gradleProjectId = '$gradleProjectId'
    def runAppTaskName = '$runAppTaskName'
    def mainClass = '${mainClass.qualifiedName}'
    def javaExePath = '$javaExePath'
    def _workingDir = ${if (workingDir.isNullOrEmpty()) "null\n" else "'$workingDir'\n"}
    def sourceSetName = '$sourceSetName'
    def javaModuleName = ${if (javaModuleName == null) "null\n" else "'$javaModuleName'\n"}

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
    
                    main = mainClass
                    ${argsString(params)}
                    if(_workingDir) workingDir = _workingDir
                    standardInput = System.in
                    if(javaModuleName) {
                        inputs.property('moduleName', javaModuleName)
                        doFirst {
                            jvmArgs += [
                                    '--module-path', classpath.asPath,
                                    '--module', javaModuleName + '/' + mainClass
                            ]
                            classpath = files()
                        }
                    }
                }
            }
        }
    }
    """
            // @formatter:on
            return initScript
        }
}