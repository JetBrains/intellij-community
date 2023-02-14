// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.gradleJava.configuration

import com.intellij.openapi.diagnostic.Logger
import com.intellij.util.Consumer
import org.jetbrains.kotlin.idea.extensions.KotlinJvmDebuggerFacade
import org.jetbrains.plugins.gradle.service.project.AbstractProjectResolverExtension

class KotlinGradleCoroutineDebugProjectResolver : AbstractProjectResolverExtension() {
    val log = Logger.getInstance(this::class.java)

    override fun enhanceTaskProcessing(taskNames: MutableList<String>, initScriptConsumer: Consumer<String>, parameters: Map<String, String>) {
        try {
            val allowCoroutineAgent = KotlinJvmDebuggerFacade.instance?.isCoroutineAgentAllowedInDebug ?: false
            if (allowCoroutineAgent && parameters.containsKey(DEBUG_DISPATCH_PORT_KEY)) {
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
                    if (task instanceof Test || task instanceof JavaExec) {
                        task.doFirst { Task forkedTask ->
                            def kotlinxCoroutinesCoreJar = forkedTask.classpath.find { it.name.startsWith("kotlinx-coroutines-core") }
                            if (kotlinxCoroutinesCoreJar) {
                                def results = (kotlinxCoroutinesCoreJar.getName() =~ /kotlinx-coroutines-core(\-jvm)?-(\d[\w\.\-]+)\.jar${'$'}/).findAll()
                                if (results) {
                                    def version = results.first()[2]
                                    def referenceVersion = org.gradle.util.VersionNumber.parse('1.3.7-255')
                                    if (org.gradle.util.VersionNumber.parse(version) > referenceVersion) {
                                        forkedTask.jvmArgs ("-javaagent:${'$'}{kotlinxCoroutinesCoreJar?.absolutePath}", "-ea")
                                    }
                                }
                            }
                        }
                    }
                }
            }
            """.trimIndent()
        initScriptConsumer.consume(script)
    }
}
