// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.gradleTooling.arguments

import org.gradle.api.Task
import org.jetbrains.kotlin.idea.gradleTooling.get
import org.jetbrains.kotlin.idea.gradleTooling.getMethodOrNull
import java.io.File
import java.lang.reflect.Method
import kotlin.reflect.full.memberProperties

object CompilerArgumentsExtractor {
    private const val ARGUMENT_ANNOTATION_CLASS = "org.jetbrains.kotlin.cli.common.arguments.Argument"
    private const val LEGACY_ARGUMENT_ANNOTATION_CLASS = "org.jetbrains.kotlin.com.sampullara.cli.Argument"
    private const val CREATE_COMPILER_ARGS = "createCompilerArgs"
    private const val SETUP_COMPILER_ARGS = "setupCompilerArgs"
    private val EXPLICIT_DEFAULT_OPTIONS: List<String> = listOf("jvmTarget")

    private val ARGUMENT_ANNOTATION_CLASSES = setOf(LEGACY_ARGUMENT_ANNOTATION_CLASS, ARGUMENT_ANNOTATION_CLASS)

    fun extractCompilerArgumentsFromTask(compileTask: Task, defaultsOnly: Boolean = false): ExtractedCompilerArgumentsBucket {
        val compileTaskClass = compileTask.javaClass
        val compilerArguments = compileTask[CREATE_COMPILER_ARGS]!!
        compileTaskClass.getMethodOrNull(SETUP_COMPILER_ARGS, compilerArguments::class.java, Boolean::class.java, Boolean::class.java)
            ?.doSetupCompilerArgs(compileTask, compilerArguments, defaultsOnly, false)
            ?: compileTaskClass.getMethodOrNull(SETUP_COMPILER_ARGS, compilerArguments::class.java, Boolean::class.java)
                ?.doSetupCompilerArgs(compileTask, compilerArguments, defaultsOnly)

        return prepareCompilerArgumentsBucket(compilerArguments)
    }


    private fun Method.doSetupCompilerArgs(
        compileTask: Task,
        compilerArgs: Any,
        defaultsOnly: Boolean,
        ignoreClasspathIssues: Boolean? = null
    ) {
        try {
            ignoreClasspathIssues?.also { invoke(compileTask, compilerArgs, defaultsOnly, it) }
                ?: invoke(compileTask, compilerArgs, defaultsOnly)
        } catch (e: Exception) {
            ignoreClasspathIssues?.also { if (!it) doSetupCompilerArgs(compileTask, compilerArgs, defaultsOnly, true) }
        }
    }

    @Suppress("UNCHECKED_CAST")
    fun prepareCompilerArgumentsBucket(compilerArguments: Any): ExtractedCompilerArgumentsBucket {
        val compilerArgumentsClassName = compilerArguments::class.java.name
        val defaultArgs = compilerArguments::class.java.getConstructor().newInstance()
        val compilerArgumentsPropertiesToProcess = compilerArguments.javaClass.kotlin.memberProperties
            .filter { prop ->
                prop.name in EXPLICIT_DEFAULT_OPTIONS || prop.get(compilerArguments) != prop.get(defaultArgs) && prop.annotations.any {
                    (it.javaClass.getMethodOrNull("annotationType")?.invoke(it) as? Class<*>)?.name in ARGUMENT_ANNOTATION_CLASSES
                }
            }

        val singleArguments: Map<String, String?>
        val classpathParts: Array<String>

        val allSingleArguments = compilerArgumentsPropertiesToProcess.filter { it.returnType.classifier == String::class }
            .associate { (it.get(compilerArguments) as String?).let { value -> it.name to value } }

        classpathParts = allSingleArguments["classpath"]?.split(File.pathSeparator)?.toTypedArray() ?: emptyArray()
        singleArguments = allSingleArguments.filterKeys { it != "classpath" }

        val multipleArguments = compilerArgumentsPropertiesToProcess.filter { it.returnType.classifier == Array<String>::class }
            .associate { (it.get(compilerArguments) as Array<String>?).let { value -> it.name to value } }

        val flagArguments = compilerArgumentsPropertiesToProcess.filter { it.returnType.classifier == Boolean::class }
            .associate { it.name to it.get(compilerArguments) as Boolean }

        val freeArgs = (compilerArguments.javaClass.kotlin.memberProperties.single { it.name == "freeArgs" }
            .get(compilerArguments) as List<String>)

        val internalArguments = (compilerArguments.javaClass.kotlin.memberProperties.single { it.name == "internalArguments" }
            .get(compilerArguments) as List<*>).filterNotNull()
            .map { internalArgument ->
                internalArgument.javaClass.kotlin.memberProperties.single { prop -> prop.name == "stringRepresentation" }
                    .get(internalArgument) as String
            }

        return ExtractedCompilerArgumentsBucket(
            compilerArgumentsClassName,
            singleArguments,
            classpathParts,
            multipleArguments,
            flagArguments,
            internalArguments,
            freeArgs
        )
    }
}