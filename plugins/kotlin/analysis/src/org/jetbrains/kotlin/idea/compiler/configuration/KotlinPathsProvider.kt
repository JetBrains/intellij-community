// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.compiler.configuration

import com.intellij.openapi.application.PathManager
import com.intellij.util.io.Decompressor
import org.jetbrains.kotlin.utils.KotlinPaths
import org.jetbrains.kotlin.utils.KotlinPathsFromHomeDir
import java.io.File

object KotlinPathsProvider {
    const val KOTLIN_MAVEN_GROUP_ID = "org.jetbrains.kotlin"
    const val KOTLIN_DIST_ARTIFACT_ID = "kotlin-dist-for-ide"

    fun getKotlinPaths(version: String): KotlinPaths =
        KotlinPathsFromHomeDir(File(PathManager.getSystemPath(), KOTLIN_DIST_ARTIFACT_ID).resolve(version))

    fun lazyUnpackKotlincDist(packedDist: File, version: String): File {
        val destination = getKotlinPaths(version).homePath

        val unpackedDistTimestamp = destination.lastModified()
        val packedDistTimestamp = packedDist.lastModified()
        if (unpackedDistTimestamp != 0L && packedDistTimestamp != 0L && unpackedDistTimestamp >= packedDistTimestamp) {
            return destination
        }
        destination.deleteRecursively()

        Decompressor.Zip(packedDist).extract(destination)
        check(destination.isDirectory)
        return destination
    }

    fun resolveKotlinMavenArtifact(mavenRepo: File, artifactId: String, version: String) =
        mavenRepo.resolve(KOTLIN_MAVEN_GROUP_ID.replace(".", "/"))
            .resolve(artifactId)
            .resolve(version)
            .resolve("$artifactId-$version.jar")

}
