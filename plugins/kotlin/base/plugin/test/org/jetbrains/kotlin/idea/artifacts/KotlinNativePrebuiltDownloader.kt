/*
 * Copyright 2010-2022 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */
package org.jetbrains.kotlin.idea.artifacts

import java.net.URL
import java.nio.file.Files
import java.nio.file.Path

const val NATIVE_PREBUILT_DEV_CDN_URL = "https://download-cdn.jetbrains.com/kotlin/native/builds/dev"

object KotlinNativePrebuiltDownloader {
    fun downloadFile(downloadURL: String, downloadOut: Path) {
        val url = URL(downloadURL)

        url.openStream().use {
            Files.copy(it, downloadOut)
        }
    }

    fun unpackPrebuildArchive(source: Path, target: Path) {
        TarGzipUnpacker.decompressTarGzipFile(source, target)
    }
}
