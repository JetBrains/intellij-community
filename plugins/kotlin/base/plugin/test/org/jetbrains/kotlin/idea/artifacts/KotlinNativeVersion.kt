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
    private const val kotlinGradlePluginVersion : String = "1.8.0-dev-2023"

    /** Return bootstrap version or version from properties file of specified Kotlin Gradle Plugin.
     *  Make sure localMaven has kotlin-gradle-plugin with required version for cooperative development environment.
     */
    val resolvedKotlinNativeVersion: String
        get() = getKotlinNativeVersionFromKotlinGradlePluginPropertiesFile()

    private fun getKotlinNativeVersionFromKotlinGradlePluginPropertiesFile(): String {
        val outputPath = Paths.get(PathManager.getCommunityHomePath()).resolve("out").resolve("kotlin-gradle-plugin")
            .resolve(kotlinGradlePluginVersion)

        val propertiesPath = outputPath.resolve("project.properties")

        if(!propertiesPath.exists()) {
            val kotlinGradlePluginPath: Path = KotlinMavenUtils.findArtifactOrFail(
                KotlinArtifactConstants.KOTLIN_MAVEN_GROUP_ID,
                "kotlin-gradle-plugin",
                kotlinGradlePluginVersion
            )

            kotlinGradlePluginPath.unzipTo(outputPath)
        }

        val propertiesFromKGP = Properties()
        FileInputStream(propertiesPath.toFile()).use { propertiesFromKGP.load(it) }

        return propertiesFromKGP.getProperty("kotlin.native.version")
    }
}