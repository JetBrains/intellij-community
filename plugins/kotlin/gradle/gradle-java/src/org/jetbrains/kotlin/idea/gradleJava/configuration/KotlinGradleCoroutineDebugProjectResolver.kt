// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.gradleJava.configuration

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.externalSystem.rt.execution.ForkedDebuggerHelper
import com.intellij.util.Consumer
import org.jetbrains.kotlin.idea.extensions.KotlinJvmDebuggerFacade
import org.jetbrains.plugins.gradle.service.project.AbstractProjectResolverExtension

class KotlinGradleCoroutineDebugProjectResolver : AbstractProjectResolverExtension() {
    val log = Logger.getInstance(this::class.java)

    override fun enhanceTaskProcessing(taskNames: MutableList<String>, initScriptConsumer: Consumer<String>, parameters: Map<String, String>) {
        try {
            val allowCoroutineAgent = KotlinJvmDebuggerFacade.instance?.isCoroutineAgentAllowedInDebug ?: false
            if (allowCoroutineAgent) {
                setupCoroutineAgentForJvmForkedTestTasks(initScriptConsumer)
            }
        } catch (e: Exception) {
            log.error("Gradle: not possible to attach a coroutine debugger agent.", e)
        }
    }

    private fun setupCoroutineAgentForJvmForkedTestTasks(initScriptConsumer: Consumer<String>) {
        val script =
            //language=Gradle
            """
            gradle.taskGraph.whenReady { TaskExecutionGraph taskGraph ->
                taskGraph.allTasks.each { Task task ->
                    if (!(task instanceof Test || task instanceof JavaExec)) return
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
        initScriptConsumer.consume(script)
    }
}
