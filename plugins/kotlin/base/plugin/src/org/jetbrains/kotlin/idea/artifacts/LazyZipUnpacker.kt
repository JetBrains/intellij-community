// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.artifacts

import com.intellij.util.io.Decompressor
import java.io.File
import java.security.MessageDigest

class LazyZipUnpacker(private val destination: File) {
    private val hashFile = destination.resolveSibling("$destination.md5")

    fun lazyUnpack(zip: File): File {
        if (!isUpToDate(zip)) {
            destination.deleteRecursively()
            Decompressor.Zip(zip).extract(destination)
            check(destination.isDirectory)

            hashFile.parentFile.mkdirs()
            // "Commit the state" / "Commit transaction" (should be the last step for the algorithm to be fault-tolerant)
            hashFile.writeText(calculateMd5(zip))
            check(isUpToDate(zip))
        }
        return destination
    }

    fun isUpToDate(zip: File): Boolean = hashFile.exists() && hashFile.readText().trim() == calculateMd5(zip).trim()

    private fun calculateMd5(zip: File): String =
        MessageDigest.getInstance("MD5").digest(zip.readBytes()).joinToString("") { "%02x".format(it) }
}
