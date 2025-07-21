// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.core.script.k2.definitions

import java.nio.file.Files
import kotlin.io.path.Path
import kotlin.io.path.writeText
import kotlin.script.experimental.annotations.KotlinScript
import kotlin.script.experimental.api.ScriptCompilationConfiguration
import kotlin.script.experimental.api.ScriptEvaluationConfiguration
import kotlin.script.experimental.api.asSuccess
import kotlin.script.experimental.api.constructorArgs
import kotlin.script.experimental.api.displayName
import kotlin.script.experimental.api.explainField
import kotlin.script.experimental.api.refineConfigurationBeforeEvaluate
import kotlin.script.experimental.api.scriptExecutionWrapper
import kotlin.script.experimental.api.with
import kotlin.script.experimental.host.ScriptingHostConfiguration
import kotlin.script.experimental.host.getScriptingClass
import kotlin.script.experimental.jvm.JvmGetScriptingClass


@Suppress("unused")
@KotlinScript(
    displayName = "KotlinScratchScript",
    fileExtension = "kts",
    compilationConfiguration = KotlinScratchCompilationConfiguration::class,
    hostConfiguration = KotlinScratchHostConfiguration::class,
    evaluationConfiguration = KotlinScratchEvaluationConfiguration::class,
)
abstract class KotlinScratchScript(vararg args: String)

const val KOTLIN_SCRATCH_EXPLAIN_FILE: String = "kotlin.scratch.explain.file"

private class KotlinScratchEvaluationConfiguration : ScriptEvaluationConfiguration(
    {
        refineConfigurationBeforeEvaluate { (_, config, _) ->
            config.with {
                val explainMap = mutableMapOf<String, Any?>()
                constructorArgs(explainMap)
                scriptExecutionWrapper<Any?> { action ->
                    try {
                        action()
                    } finally {
                        System.getProperty(KOTLIN_SCRATCH_EXPLAIN_FILE)?.let { location ->
                            val path = Path(location)
                            Files.createDirectories(path.parent)
                            path.writeText(explainMap.entries.joinToString(separator = "\n") { entry -> "${entry.key}=${entry.value}" })
                        }
                    }
                }
            }.asSuccess()
        }
    }
)

private class KotlinScratchCompilationConfiguration() : ScriptCompilationConfiguration(
    {
        displayName("Kotlin Scratch")
        explainField(SCRATCH_EXPLAIN_VARIABLE_NAME)
    })

private class KotlinScratchHostConfiguration : ScriptingHostConfiguration(
    {
        getScriptingClass(JvmGetScriptingClass())
    })


private const val SCRATCH_EXPLAIN_VARIABLE_NAME: String = "\$\$explain"