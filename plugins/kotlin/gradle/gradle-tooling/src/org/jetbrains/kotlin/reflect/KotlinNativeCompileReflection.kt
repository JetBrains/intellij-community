/*
 * Copyright 2010-2022 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */
package org.jetbrains.kotlin.reflect

import org.gradle.api.provider.Provider
import java.io.File

fun KotlinNativeCompileReflection(compileKotlinTask: Any): KotlinNativeCompileReflection =
    KotlinNativeCompileReflectionImpl(compileKotlinTask)

interface KotlinNativeCompileReflection {
    val destinationDir: File?
}

private class KotlinNativeCompileReflectionImpl(private val instance: Any) : KotlinNativeCompileReflection {
    override val destinationDir: File? by lazy {
        instance.callReflectiveGetter("getDestinationDir", logger)
            ?: when(val outputFile = instance.callReflective("getOutputFile", parameters(), returnType<Any>(), logger)) {
                is Provider<*> -> outputFile.orNull as? File
                is File -> outputFile
                else -> null
            }?.parentFile
    }

    companion object {
        private val logger: ReflectionLogger = ReflectionLogger(KotlinNativeCompileReflection::class.java)
    }
}