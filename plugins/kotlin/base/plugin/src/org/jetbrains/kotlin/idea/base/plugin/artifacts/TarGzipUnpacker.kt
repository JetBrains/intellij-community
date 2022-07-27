// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.base.plugin.artifacts

import org.apache.commons.compress.archivers.ArchiveEntry
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream
import java.io.BufferedInputStream
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption


object TarGzipUnpacker {
    @Throws(IOException::class)
    fun decompressTarGzipFile(source: Path, target: Path) {
        if (Files.notExists(source)) {
            throw IOException("File doesn't exists!")
        }
        Files.newInputStream(source).use { fi ->
            BufferedInputStream(fi).use { bi ->
                GzipCompressorInputStream(bi).use { gzi ->
                    TarArchiveInputStream(gzi).use { ti ->
                        lateinit var entry: ArchiveEntry
                        while (ti.nextEntry?.also { entry = it } != null) {

                            val newPath: Path = zipSlipProtect(entry, target)
                            if (entry.isDirectory) {
                                Files.createDirectories(newPath)
                            } else {
                                val parent = newPath.parent
                                if (parent != null) {
                                    if (Files.notExists(parent)) {
                                        Files.createDirectories(parent)
                                    }
                                }

                                Files.copy(ti, newPath, StandardCopyOption.REPLACE_EXISTING)

                            }
                        }
                    }
                }
            }
        }
    }

    @Throws(IOException::class)
    private fun zipSlipProtect(entry: ArchiveEntry, targetDir: Path): Path {
        val targetDirResolved = targetDir.resolve(entry.name)

        val normalizePath = targetDirResolved.normalize()
        if (!normalizePath.startsWith(targetDir)) {
            throw IOException("Bad entry: " + entry.name)
        }
        return normalizePath
    }
}