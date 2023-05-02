// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.base.plugin.artifacts

import com.intellij.util.io.Decompressor
import com.intellij.util.io.DigestUtil
import com.intellij.util.io.hashToHexString
import java.io.File
import java.security.MessageDigest

internal class LazyZipUnpacker(private val destination: File) : AbstractLazyFileOutputProducer<File, Unit>(
    // Use hash to get some unique string originated from destination.path which can be used in filename
    // (unfortunately, destination.path itself cannot be used as a filename because of slashes)
    "${LazyZipUnpacker::class.java.name}-${hashToHexString(destination.canonicalPath, DigestUtil.md5())}"
) {

    override fun produceOutput(input: File, computationContext: Unit): List<File> { // input is a zip file
        destination.deleteRecursively()
        Decompressor.Zip(input).extract(destination)
        check(destination.isDirectory)
        return listOf(destination)
    }

    override fun updateMessageDigestWithInput(messageDigest: MessageDigest, input: File, buffer: ByteArray) {
        DigestUtil.updateContentHash(messageDigest, input.toPath(), buffer)
    }

    fun lazyUnpack(zip: File) = lazyProduceOutput(zip, Unit).singleOrNull()
        ?: error("${LazyZipUnpacker::produceOutput.name} returns only single element")

    fun getUnpackedIfUpToDateOrNull(zip: File): File? = getOutputIfUpToDateOrEmpty(zip).ifEmpty { return null }.singleOrNull()
        ?: error("${LazyZipUnpacker::produceOutput.name} returns only single element")
}
