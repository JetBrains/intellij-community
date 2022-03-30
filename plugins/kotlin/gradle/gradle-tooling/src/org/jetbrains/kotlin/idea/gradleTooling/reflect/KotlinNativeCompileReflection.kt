// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.gradleTooling.reflect

import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.Provider
import org.jetbrains.kotlin.idea.gradleTooling.getMethodOrNull
import java.io.File

fun KotlinNativeCompileReflection(compileKotlinTask: Any): KotlinNativeCompileReflection =
    KotlinNativeCompileReflectionImpl(compileKotlinTask)

interface KotlinNativeCompileReflection {
    val destinationDir: File?
}

private class KotlinNativeCompileReflectionImpl(private val instance: Any) : KotlinNativeCompileReflection {
    override val destinationDir: File? by lazy {
        val instanceClazz = instance::class.java
        if (instanceClazz.getMethodOrNull("getDestinationDirectory") != null)
            instance.callReflectiveGetter<DirectoryProperty>("getDestinationDirectory", logger)?.asFile?.get()
        else instance.callReflectiveGetter("getDestinationDir", logger)
            ?: when (val outputFile = instance.callReflectiveAnyGetter("getOutputFile", logger)) {
                is Provider<*> -> outputFile.orNull as? File
                is File -> outputFile
                else -> null
            }?.parentFile
    }

    companion object {
        private val logger: ReflectionLogger = ReflectionLogger(KotlinNativeCompileReflection::class.java)
    }
}