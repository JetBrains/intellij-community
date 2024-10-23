// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.gradleJava.configuration

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskId
import com.intellij.openapi.externalSystem.rt.execution.ForkedDebuggerHelper
import kotlinx.coroutines.DEBUG_PROPERTY_NAME
import kotlinx.coroutines.DEBUG_PROPERTY_VALUE_OFF
import org.gradle.util.GradleVersion
import org.jetbrains.kotlin.idea.extensions.KotlinJvmDebuggerFacade
import org.jetbrains.plugins.gradle.service.execution.joinInitScripts
import org.jetbrains.plugins.gradle.service.execution.loadCommonTasksUtilsScript
import org.jetbrains.plugins.gradle.service.task.GradleTaskManagerExtension
import org.jetbrains.plugins.gradle.settings.GradleExecutionSettings

class KotlinCoroutineDebugGradleTaskManagerExtension : GradleTaskManagerExtension {

    companion object {

        private val LOG = Logger.getInstance(this::class.java)

        private const val MIN_SUPPORTED_GRADLE_VERSION = "4.6" // CommandLineArgumentProvider is available only since Gradle 4.6

        private const val KOTLIN_COROUTINE_DEBUG_SCRIPT_NAME = "ijKotlinCoroutineDebugInit"
    }

    override fun configureTasks(
        projectPath: String,
        id: ExternalSystemTaskId,
        settings: GradleExecutionSettings,
        gradleVersion: GradleVersion?
    ) {
        try {
            val allowCoroutineAgent = KotlinJvmDebuggerFacade.instance?.isCoroutineAgentAllowedInDebug ?: false
            if (allowCoroutineAgent && (gradleVersion == null || gradleVersion >= GradleVersion.version(MIN_SUPPORTED_GRADLE_VERSION))) {
                val initScript = joinInitScripts(
                    loadCommonTasksUtilsScript(),
                    createCoroutineDebuggerScript(gradleVersion == null)
                )
                settings.addInitScript(KOTLIN_COROUTINE_DEBUG_SCRIPT_NAME, initScript)
            }

        } catch (e: Exception) {
            LOG.error("Gradle: not possible to attach a coroutine debugger agent.", e)
        }
    }

    private fun createCoroutineDebuggerScript(shouldCheckGradleVersion: Boolean): String {
        val gradleVersionCheck = if (shouldCheckGradleVersion) {
            //language=Gradle
            """
                if (org.gradle.util.GradleVersion.current() < org.gradle.util.GradleVersion.version("$MIN_SUPPORTED_GRADLE_VERSION")) return
            """.trimIndent()
        } else {
            ""
        }
        //language=Gradle
        return """
            gradle.taskGraph.whenReady { TaskExecutionGraph taskGraph ->
                $gradleVersionCheck
                def debugAllIsEnabled = Boolean.valueOf(System.properties["idea.gradle.debug.all"])
                def jvmTasks = taskGraph.allTasks.findAll { task -> task instanceof Test || (task instanceof JavaExecSpec && task instanceof JavaForkOptions) }
                def matchedTasks = debugAllIsEnabled ? jvmTasks : GradleTasksUtil.filterStartTasks(jvmTasks, gradle, rootProject)

                matchedTasks.each { Task task ->                    
                    for (arg in task.getJvmArgs()) {
                        if (arg == "-D$DEBUG_PROPERTY_NAME=$DEBUG_PROPERTY_VALUE_OFF") {
                            return
                        }
                    }

                    FileCollection taskClasspath = task.classpath
                    task.jvmArgumentProviders.add(new CommandLineArgumentProvider() {
                        private static def VERSION_PATTERN = java.util.regex.Pattern.compile(/(\d+)\.(\d+)(\.(\d+))?.*/)
                    
                        @Override
                        Iterable<String> asArguments() {
                            List<String> emptyList = Collections.emptyList()
                            if (System.getProperty("${ForkedDebuggerHelper.DISPATCH_PORT_SYS_PROP}") == null) return emptyList
                            def kotlinxCoroutinesCoreJar = taskClasspath.find { it.name.startsWith("kotlinx-coroutines-core") && !it.name.contains("sources") }
                            if (kotlinxCoroutinesCoreJar == null) return emptyList
                            def results = (kotlinxCoroutinesCoreJar.getName() =~ /kotlinx-coroutines-core(\-jvm)?-(\d[\w\.\-]+)\.jar${'$'}/).findAll()
                            if (results.isEmpty()) return emptyList
                            String version = results.first()[2]
                            def matcher = VERSION_PATTERN.matcher(version)
                            try {
                                if (!matcher.matches()) return emptyList
                                int major = Integer.parseInt(matcher.group(1)) 
                                int minor = Integer.parseInt(matcher.group(2))
                                int patch = Integer.parseInt(matcher.group(4) ?: "0")
                                if (major < 1 || (major == 1 && (minor < 5 || (minor == 5 && patch < 1)))) return emptyList
                            } catch (NumberFormatException ignored) {
                                return emptyList
                            }
                            return ["-javaagent:${'$'}{kotlinxCoroutinesCoreJar?.absolutePath}", "-ea"]
                        }
                    })
                }
            }
            """.trimIndent()
    }
}
