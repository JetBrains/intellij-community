// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.compiler.configuration

import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.openapi.application.PathManager
import org.jetbrains.kotlin.config.KotlinCompilerVersion
import java.io.File

sealed interface KotlinPluginLayout {
    val kotlinc: File
    val jpsPluginJar: File

    companion object {
        const val KOTLIN_JPS_PLUGIN_CLASSPATH_ARTIFACT_ID = "kotlin-jps-plugin-classpath"

        fun getInstance(): KotlinPluginLayout {
            if (PluginManagerCore.isRunningFromSources()) {
                return KotlinPluginLayoutWhenRunFromSources
            } else {
                val jarInsideLib = PathManager.getJarPathForClass(KotlinPluginLayout::class.java)
                    ?.let { File(it) }
                    ?: error("Can't find jar file for ${KotlinPluginLayout::class.simpleName}")
                check(jarInsideLib.extension == "jar") { "$jarInsideLib should be jar file" }
                return KotlinPluginLayoutWhenRunInProduction(jarInsideLib.parentFile.also { check(it.name == "lib") { "$it should be lib directory" } }.parentFile)
            }
        }
    }
}

private class KotlinPluginLayoutWhenRunInProduction(private val root: File) : KotlinPluginLayout {
    init {
        check(root.exists()) { "$root doesn't exist" }
    }

    private fun resolve(path: String) = root.resolve(path).also { check(it.exists()) { "$it doesn't exist" } }

    override val kotlinc: File get() = resolve("kotlinc")
    override val jpsPluginJar: File get() = resolve("lib/jps/kotlin-jps-plugin.jar")
}

private object KotlinPluginLayoutWhenRunFromSources : KotlinPluginLayout {
    override val kotlinc: File
        get() {
            val anyJarInMavenLocal = PathManager.getJarPathForClass(KotlinVersion::class.java)?.let { File(it) }
                ?: error("Can't find kotlin-stdlib.jar in maven local")
            // We can't use getExpectedMavenArtifactJarPath because it will cause cyclic "Path macros" service initialization
            val packedDist = generateSequence(anyJarInMavenLocal) { it.parentFile }
                .map {
                    KotlinPathsProvider.resolveMavenArtifactInMavenRepo(
                        it,
                        KotlinPathsProvider.KOTLIN_DIST_ARTIFACT_ID,
                        KotlinCompilerVersion.VERSION
                    )
                }
                .firstOrNull { it.exists() }
                ?: error(
                    "Can't find kotlinc-dist in local maven. But IDEA should have downloaded it because 'kotlinc.kotlin-dist' " +
                            "library is specified as dependency for 'kotlin.util.compiler-dependencies' module"
                )

            return KotlinPathsProvider.lazyUnpackKotlincDist(packedDist, KotlinCompilerVersion.VERSION)
        }

    override val jpsPluginJar: File
        get() = KotlinPathsProvider.getExpectedMavenArtifactJarPath(
            KotlinPluginLayout.KOTLIN_JPS_PLUGIN_CLASSPATH_ARTIFACT_ID,
            KotlinCompilerVersion.VERSION
        )
}
