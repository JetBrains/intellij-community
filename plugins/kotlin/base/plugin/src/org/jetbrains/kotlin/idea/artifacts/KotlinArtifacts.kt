// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.artifacts

import com.intellij.openapi.application.PathManager
import org.jetbrains.kotlin.idea.compiler.configuration.KotlinPluginLayout
import java.io.File
import org.jetbrains.kotlin.idea.base.plugin.artifacts.KotlinArtifacts as KotlinArtifactsNew

@Suppress("unused")
@Deprecated("Use 'org.jetbrains.kotlin.idea.base.plugin.artifacts.KotlinArtifacts' instead")
class KotlinArtifacts private constructor(val kotlincDirectory: File) {
    companion object {
        val KOTLIN_DIST_LOCATION_PREFIX: File = File(PathManager.getSystemPath(), "kotlin-dist-for-ide")

        @get:JvmStatic
        @Deprecated("Use 'org.jetbrains.kotlin.idea.base.plugin.artifacts.KotlinArtifacts' instead")
        val instance: KotlinArtifacts by lazy {
            KotlinArtifacts(KotlinPluginLayout.kotlinc)
        }
    }

    val kotlinStdlib: File get() = KotlinArtifactsNew.kotlinStdlib
    val kotlinStdlibSources: File get() = KotlinArtifactsNew.kotlinStdlibSources
    val kotlinStdlibJdk7: File get() = KotlinArtifactsNew.kotlinStdlibJdk7
    val kotlinStdlibJdk7Sources: File get() = KotlinArtifactsNew.kotlinStdlibJdk7Sources
    val kotlinStdlibJdk8: File get() = KotlinArtifactsNew.kotlinStdlibJdk8
    val kotlinStdlibJdk8Sources: File get() = KotlinArtifactsNew.kotlinStdlibJdk8Sources
    val kotlinReflect: File get() = KotlinArtifactsNew.kotlinReflect
    val kotlinStdlibJs: File get() = KotlinArtifactsNew.kotlinStdlibJs
    val kotlinMainKts: File get() = KotlinArtifactsNew.kotlinMainKts
    val kotlinScriptRuntime: File get() = KotlinArtifactsNew.kotlinScriptRuntime
}
