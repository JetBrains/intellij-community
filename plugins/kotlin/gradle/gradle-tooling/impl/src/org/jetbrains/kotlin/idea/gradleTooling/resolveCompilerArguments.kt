// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.gradleTooling

import org.gradle.api.Task
import org.jetbrains.kotlin.idea.gradleTooling.reflect.KotlinCompileTaskReflection
import org.jetbrains.kotlin.idea.gradleTooling.reflect.KotlinCompilerArgumentsResolverReflection
import org.jetbrains.kotlin.idea.gradleTooling.reflect.ReflectionLogger
import java.lang.reflect.Method

internal fun resolveCompilerArguments(compileTask: Task): List<String>? {
    /* Service bundled inside Kotlin Gradle Plugin */
    val compilerArgumentsResolver = KotlinCompilerArgumentsResolverReflection(compileTask.project, compileTask::class.java.classLoader)

    return when {
        /*
        Preferred approach in Kotlin 1.9+:
        Using a service inside KGP that is capable of resolving the arguments properly
        */
        compilerArgumentsResolver != null -> compilerArgumentsResolver.resolveCompilerArguments(compileTask)

        /*
        Fallback for Kotlin Gradle Plugins < 1.9:
        Using the 'CompilerArgumentsAware' infrastructure in KGP, calling into it via reflection and
        fixing up known issues in KGP reflectively.

        Native Compile Tasks however do only support requesting the arguments in serialised format.
        Other compile tasks will allow the createCompilerArgs + setupCompilerArgs approach
         */
        compileTask.isKotlinNativeCompileTask -> resolveCompilerArgumentsForKGPLower19ForNativeCompileTask(compileTask)
        else -> resolveCompilerArgumentsForKGPLower19(compileTask)
    }
}

fun resolveCompilerArgumentsForKGPLower19ForNativeCompileTask(compileTask: Task): List<String> {
    /*
    Resolve arguments using 'serializedCompilerArguments' as native tasks do not support createCompilerArgs&setupCompilerArgs methods
     */
    return KotlinCompileTaskReflection(compileTask).serializedCompilerArguments
}


fun resolveCompilerArgumentsForKGPLower19(compileTask: Task): List<String>? {
    val logger = ReflectionLogger(compileTask.javaClass)

    /*
    Create compiler arguments by using the CompilerArgumentsAware methods
    (createCompilerArgs followed by setupCompilerArgs)
     */
    val compileTaskClass = compileTask.javaClass
    val compilerArguments = compileTask["createCompilerArgs"] ?: run {
        logger.logIssue("Failed creating compiler arguments from ${compileTask.path}")
        return null
    }

    val setupCompilerArgsMethod = compileTaskClass.getMethodOrNull(
        "setupCompilerArgs", compilerArguments::class.java, Boolean::class.java, Boolean::class.java
    ) ?: run {
        logger.logIssue("No 'setupCompilerArgs' method found on '${compileTask.path}'")
        return null
    }
    setupCompilerArgsMethod.doSetupCompilerArgs(compileTask, compilerArguments)

    /*
    Previous Kotlin Gradle Plugin versions (<1.9) are resolving the classpath of the compile task, adding it
    directly to the arguments .classpath property. This classpath is undesired as it potentially takes too much memory.
    JPS builds will be able to infer the classpath from the IJ project model.

    We therefore will remove the classpath manually from the received compiler arguments and then convert the arguments
    to their List<String> representation.
   */
    val compilerArgumentsClass = compilerArguments.javaClass
    compilerArgumentsClass.getMethodOrNull("setClasspath", String::class.java)?.invoke(compilerArguments, null)


    /*
    Use the 'ArgumentUtils' list bundled within the Kotlin Gradle Plugin to convert
    the arguments to List<String>
     */
    return runCatching {
        val commonToolArgumentsClass = compileTaskClass.classLoader.loadClass(
            "org.jetbrains.kotlin.cli.common.arguments.CommonToolArguments"
        )

        val argumentUtilsClass = compileTaskClass.classLoader.loadClass(
            "org.jetbrains.kotlin.compilerRunner.ArgumentUtils"
        )

        val toStringListFunction = argumentUtilsClass.getMethodOrNull("convertArgumentsToStringList", commonToolArgumentsClass) ?: run {
            logger.logIssue("Missing 'convertArgumentsToStringList' function")
            return null
        }

        /*
        Kotlin Gradle Plugin versions before:
        ```
        Switch `-jvm-target` default to null Mikhael Bogdanov* 14.11.21, 14:23
        f5da166d7c5efe34541bee0cf207f6b394aca5e5
        ```

        Did set a default jvmTarget value, basically omitting the argument on String conversion (since it's the default value)
        This however is undesirable, since there are Kotlin Gradle Plugins build with jvmTarget=1.6 as default.
        When omitting the String argument, then the IDE will fill the absent argument with its default (which might be 1.8 or higher)

        Therefore, we detect this Situation and support older Kotlin Gradle Plugins by explicitly adding this argument again.
         */
        val additionalExplicitArguments: List<String> = runCatching resolveExplicitArguments@{
            val emptyArgumentsInstance = compilerArgumentsClass.getConstructor().newInstance()
            val getJvmTargetMethod = compilerArgumentsClass.getMethodOrNull("getJvmTarget") ?: return@resolveExplicitArguments emptyList()
            val jvmTarget = getJvmTargetMethod.invoke(compilerArguments)
            val jvmTargetDefault = getJvmTargetMethod.invoke(emptyArgumentsInstance)

            if (jvmTargetDefault != null && jvmTargetDefault == jvmTarget) {
                listOf("-jvm-target", jvmTarget.toString())
            } else emptyList()

        }.getOrNull().orEmpty()

        @Suppress("UNCHECKED_CAST")
        val argumentsAsStrings = (toStringListFunction.invoke(null, compilerArguments) as? List<String>) ?: return null
        additionalExplicitArguments + argumentsAsStrings
    }
        .onFailure { failure -> logger.logIssue("Failed converting $compilerArguments", failure) }
        .getOrNull()
}


private fun Method.doSetupCompilerArgs(compileTask: Task, compilerArgs: Any) {
    runCatching {
        invoke(compileTask, compilerArgs, /*defaults only */ false,  /* ignore classpath issues */ false)
    }.recoverCatching {
        invoke(compileTask, compilerArgs, /* defaults only */ false, /* ignore classpath issues */ true)
    }
}


private const val KOTLIN_NATIVE_COMPILE_CLASS = "org.jetbrains.kotlin.gradle.tasks.AbstractKotlinNativeCompile"
private val Task.isKotlinNativeCompileTask: Boolean
    get() = javaClass.classLoader.loadClassOrNull(KOTLIN_NATIVE_COMPILE_CLASS)?.isAssignableFrom(javaClass) ?: false