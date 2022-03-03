// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.gradleTooling.reflect

import org.gradle.api.Task
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.Exec
import org.jetbrains.kotlin.idea.gradleTooling.loadClassOrNull
import java.io.File

fun KonanArtifactReflection(konanArtifact: Any): KonanArtifactReflection = KonanArtifactReflectionImpl(konanArtifact)

interface KonanArtifactReflection {
    val executableName: String?
    val compilationTargetName: String?
    val outputKindType: String?
    val konanTargetName: String?
    val outputFile: File?
    val linkTaskPath: String?
    val runTask: Exec?
    val isTests: Boolean?
    val freeCompilerArgs: Collection<String>?
}

private class KonanArtifactReflectionImpl(private val instance: Any) : KonanArtifactReflection {
    override val executableName: String? by lazy {
        instance.callReflectiveGetter("getBaseName", logger)
    }
    private val linkTask: Task? by lazy {
        instance.callReflectiveGetter("getLinkTask", logger)
    }
    override val linkTaskPath: String? by lazy {
        linkTask?.path
    }
    override val runTask: Exec? by lazy {
        if (instance.javaClass.classLoader.loadClassOrNull(NATIVE_EXECUTABLE_CLASS)?.isInstance(instance) == true)
            instance.callReflectiveGetter("getRunTask", logger)
        else null
    }

    override val compilationTargetName: String? by lazy {
        val compilation = linkTask?.callReflectiveAnyGetter("getCompilation", logger)?.let {
            when (it) {
                is Provider<*> -> it.get()
                else -> it
            }
        }
        compilation?.callReflectiveAnyGetter("getTarget", logger)
            ?.callReflectiveGetter("getName", logger)
    }
    override val outputKindType: String? by lazy {
        linkTask?.callReflectiveAnyGetter("getOutputKind", logger)
            ?.callReflectiveGetter("name", logger)
    }
    override val konanTargetName: String? by lazy {
        linkTask?.callReflectiveGetter("getTarget", logger)
    }
    override val outputFile: File? by lazy {
        when (val outputFile = instance.callReflective("getOutputFile", parameters(), returnType<Any>(), logger)) {
            is Provider<*> -> outputFile.orNull as? File
            is File -> outputFile
            else -> null
        }
    }
    override val isTests: Boolean? by lazy {
        linkTask?.callReflectiveGetter("getProcessTests", logger)
    }

    override val freeCompilerArgs: Collection<String>? by lazy {
        linkTask?.callReflectiveAnyGetter("getKotlinOptions", logger)
            ?.callReflectiveGetter("getFreeCompilerArgs", logger)
    }

    companion object {
        private val logger: ReflectionLogger = ReflectionLogger(KonanArtifactReflection::class.java)
        private const val NATIVE_EXECUTABLE_CLASS = "org.jetbrains.kotlin.gradle.plugin.mpp.Executable"
    }
}
