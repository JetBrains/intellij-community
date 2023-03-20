/*
 * Copyright 2010-2022 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */
package org.jetbrains.kotlin.idea.artifacts

import org.jetbrains.kotlin.konan.file.unzipTo
import java.io.IOException
import java.net.URL
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.absolute

const val NATIVE_PREBUILT_DEV_CDN_URL = "https://download-cdn.jetbrains.com/kotlin/native/builds/dev"
const val NATIVE_PREBUILT_RELEASE_CDN_URL = "https://download-cdn.jetbrains.com/kotlin/native/builds/releases"

object KotlinNativePrebuiltDownloader {
    fun downloadFile(downloadURL: String, downloadOut: Path) {
        val url = URL(downloadURL)

        url.openStream().use {
            Files.copy(it, downloadOut)
        }
    }

    @Throws(IOException::class)
    fun unpackPrebuildArchive(source: Path, target: Path) {
        if (source.toString().endsWith(".tar.gz")) {
            TarGzipUnpacker.decompressTarGzipFile(source, target)
        }
        else if (source.toString().endsWith(".zip")) {
            source.unzipTo(target)
        }
        else {
            throw IOException("Can't unpack ${source.absolute()}. Support only .tar.gz and .zip files.")
        }
    }
}
