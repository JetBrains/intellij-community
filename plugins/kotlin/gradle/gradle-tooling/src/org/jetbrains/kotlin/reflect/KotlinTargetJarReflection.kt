/*
 * Copyright 2010-2022 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */
package org.jetbrains.kotlin.reflect

import org.gradle.api.file.RegularFile
import org.gradle.api.provider.Provider
import org.jetbrains.kotlin.gradle.getMethodOrNull
import java.io.File

fun KotlinTargetJarReflection(artifactsTask: Any): KotlinTargetJarReflection = KotlinTargetJarReflectionImpl(artifactsTask)

interface KotlinTargetJarReflection {
    val archiveFile: File?
}

private class KotlinTargetJarReflectionImpl(private val instance: Any) : KotlinTargetJarReflection {
    override val archiveFile: File? by lazy {
        if (instance.javaClass.getMethodOrNull("getArchiveFile") != null) {
            instance.callReflective("getArchiveFile", parameters(), returnType<Provider<RegularFile>>(), logger)?.orNull?.asFile
        } else null
    }

    companion object {
        private val logger: ReflectionLogger = ReflectionLogger(KotlinTargetJarReflection::class.java)
    }
}