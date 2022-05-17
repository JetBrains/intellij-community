// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.artifacts

import com.intellij.util.io.Decompressor
import java.io.File
import java.io.InputStream
import java.security.MessageDigest

class LazyZipUnpacker(private val destination: File) : AbstractLazyFileOutputProducer<File>(
    // Use hash to get some unique string originated from destination.path which can be used in filename
    // (unfortunately, destination.path itself cannot be used as a filename because of slashes)
    "${LazyZipUnpacker::class.java.name}-${destination.canonicalPath.byteInputStream().use { it.md5() }}"
) {

    override fun produceOutput(input: File): List<File> { // input is a zip file
        destination.deleteRecursively()
        Decompressor.Zip(input).extract(destination)
        check(destination.isDirectory)
        return listOf(destination)
    }

    override fun updateMessageDigestWithInput(messageDigest: MessageDigest, input: File, buffer: ByteArray) {
        input.inputStream().use { messageDigest.update(it, buffer) }
    }

    fun lazyUnpack(zip: File) = lazyProduceOutput(zip).singleOrNull() ?: error("${LazyZipUnpacker::produceOutput.name} returns only single element")
}

private fun InputStream.md5(): String {
    val messageDigest = MessageDigest.getInstance("MD5")
    messageDigest.update(this)
    return messageDigest.digest().joinToString("") { "%02x".format(it) }
}
