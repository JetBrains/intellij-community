/*
 * Copyright 2010-2022 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */
package org.jetbrains.kotlin.idea.artifacts

import com.intellij.openapi.application.PathManager
import kotlinx.coroutines.runBlocking
import org.jetbrains.intellij.build.dependencies.BuildDependenciesCommunityRoot
import org.jetbrains.intellij.build.dependencies.BuildDependenciesDownloader.extractFile
import org.jetbrains.intellij.build.downloadFileToCacheLocationSync
import org.jetbrains.kotlin.konan.target.HostManager
import org.jetbrains.kotlin.konan.target.TargetSupportException
import java.nio.file.Path
import kotlin.io.path.exists

const val NATIVE_PREBUILT_DEV_CDN_URL = "https://download-cdn.jetbrains.com/kotlin/native/builds/dev"
const val NATIVE_PREBUILT_RELEASE_CDN_URL = "https://download-cdn.jetbrains.com/kotlin/native/builds/releases"

@Throws(TargetSupportException::class)
fun getNativePrebuilt(version: String, platform: String, communityRoot: BuildDependenciesCommunityRoot): Path {
    if (!KotlinNativeHostSupportDetector.isNativeHostSupported() && platform == HostManager.platformName())
        throw TargetSupportException("kotlin-native-prebuilt can't be downloaded as it doesn't exist for the host: ${platform}")

    val prebuilt = "kotlin-native-prebuilt-$platform-$version"
    val archiveName = if (HostManager.hostIsMingw) "$prebuilt.zip" else "$prebuilt.tar.gz"
    val cdnUrl = if ("dev" in version) NATIVE_PREBUILT_DEV_CDN_URL else NATIVE_PREBUILT_RELEASE_CDN_URL
    val downloadUrl = "$cdnUrl/$version/$platform/$archiveName"
    val outDir = Path.of(PathManager.getCommunityHomePath()).resolve("out")
    val expandedDir = outDir.resolve(archiveName)
    val target = expandedDir.resolve(prebuilt)

    if (!target.exists()) {
        val archiveFilePath = downloadFileToCacheLocationSync(downloadUrl, communityRoot)
        runBlocking {
            extractFile(archiveFilePath, expandedDir, communityRoot)
        }
    }

    return target
}
