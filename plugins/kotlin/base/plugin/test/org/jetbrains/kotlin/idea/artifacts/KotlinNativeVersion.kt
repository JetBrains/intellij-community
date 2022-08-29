/*
 * Copyright 2010-2022 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */
package org.jetbrains.kotlin.idea.artifacts

import com.intellij.openapi.application.PathManager
import org.jetbrains.kotlin.idea.base.plugin.artifacts.KotlinArtifactConstants
import org.jetbrains.kotlin.idea.base.plugin.artifacts.kotlincStdlibFileName
import org.jetbrains.kotlin.idea.compiler.configuration.KotlinMavenUtils
import org.jetbrains.kotlin.konan.file.unzipTo
import java.io.FileInputStream
import java.nio.file.Path
import java.nio.file.Paths
import java.util.*

object KotlinNativeVersion {
    /** This field is automatically setup from project-module-updater.
     *  See [org.jetbrains.tools.model.updater.updateKGPVersionForKotlinNativeTests]
     */
    private val kotlinGradlePluginVersion :String? = null

    /** Return bootstrap version or version from properties file of specified Kotlin Gradle Plugin.
     *  Make sure localMaven has kotlin-gradle-plugin with required version for cooperative development environment.
     */
    val resolvedKotlinNativeVersion: String
        get() {
            if (kotlinGradlePluginVersion != null) {
                return getKotlinNativeVersionFromKotlinGradlePluginPropertiesFile(kotlinGradlePluginVersion)
            }
            else {
                return KotlinMavenUtils.findLibraryVersion(kotlincStdlibFileName) ?: error("Can't get '$kotlincStdlibFileName' version")
            }

        }

    private fun getKotlinNativeVersionFromKotlinGradlePluginPropertiesFile(version: String): String {
        val outputPath = Paths.get(PathManager.getCommunityHomePath()).resolve("out").resolve("kotlin-gradle-plugin")
        val propertiesPath = outputPath.resolve("project.properties")

        val kotlinGradlePluginPath: Path = KotlinMavenUtils.findArtifactOrFail(
            KotlinArtifactConstants.KOTLIN_MAVEN_GROUP_ID,
            "kotlin-gradle-plugin",
            version
        )

        kotlinGradlePluginPath.unzipTo(outputPath)

        val propertiesFromKGP = Properties()
        FileInputStream(propertiesPath.toFile()).use { propertiesFromKGP.load(it) }

        return propertiesFromKGP.getProperty("kotlin.native.version")
    }
}