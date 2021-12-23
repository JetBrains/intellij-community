// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.gradleTooling.reflect

import org.gradle.api.file.RegularFile
import org.gradle.api.provider.Provider
import org.jetbrains.kotlin.idea.gradleTooling.getMethodOrNull
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