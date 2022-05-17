// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.artifacts

import com.intellij.ide.caches.CachesInvalidator
import com.intellij.openapi.application.PathManager
import java.io.File
import java.io.InputStream
import java.security.MessageDigest

private const val BUFFER_SIZE = 4096
private val ROOT = File(PathManager.getSystemPath()).resolve("kotlin-lazy-file-pipeline-cache")

/**
 * - Re-calculates output when one of the inputs changes
 * - Re-calculates input when one of the outputs changes
 * - Supports "Invalidate caches"
 */
abstract class AbstractLazyFileOutputProducer<I : Any>(uniquePipelineId: String) {
    private val hashFile = ROOT.resolve("$uniquePipelineId.hash")
    private val outputsFile = ROOT.resolve("$uniquePipelineId.outputs")

    private var outputs: List<File> = emptyList()
        get() = field.takeIf { it.isNotEmpty() } ?: readOutputsFile().also { field = it }
        set(value) {
            field = value.map { it.canonicalFile }
            outputsFile.parentFile.mkdirs()
            outputsFile.writeText(field.joinToString("\n"))
        }

    protected fun lazyProduceOutput(input: I): List<File> {
        if (!isUpToDate(input)) {
            outputs = produceOutput(input).ifEmpty { return emptyList() }

            hashFile.parentFile.mkdirs()
            // "Commit the state" / "Commit transaction" (should be the last step for the algorithm to be fault-tolerant)
            hashFile.writeText(calculateMd5(input))
            check(isUpToDate(input)) { "It should be up-to-date after re-calculation" }
        }
        return outputs
    }

    fun isUpToDate(input: I): Boolean = outputs.all { it.exists() } && hashFile.isFile && outputsFile.isFile &&
            hashFile.readText().trim() == calculateMd5(input)

    protected abstract fun produceOutput(input: I): List<File>
    protected abstract fun updateMessageDigestWithInput(messageDigest: MessageDigest, input: I, buffer: ByteArray)

    private fun readOutputsFile() = outputsFile.takeIf { it.exists() }
        ?.useLines { lines ->
            lines.map { File(it).canonicalFile }.toList()
        } ?: emptyList()

    private fun calculateMd5(input: I): String {
        val messageDigest = MessageDigest.getInstance("MD5")
        val buffer = ByteArray(BUFFER_SIZE)
        // hash outputsFile too to make sure that user doesn't change this file
        outputsFile.inputStream().use { messageDigest.update(it, buffer) }
        updateMessageDigestWithInput(messageDigest, input, buffer)
        messageDigest.update(outputs, buffer)
        return messageDigest.digest().joinToString("") { "%02x".format(it) }
    }

    companion object {
        fun invalidateCaches() {
            ROOT.deleteRecursively()
        }
    }
}

class LazyFileOutputProducerCacheInvalidator : CachesInvalidator() {
    override fun invalidateCaches() {
        AbstractLazyFileOutputProducer.invalidateCaches()
    }
}

private fun MessageDigest.update(files: List<File>, buffer: ByteArray) {
    val messageDigest = this
    files.flatMap { it.flattenDir() }
        .map { it.canonicalFile }
        .sorted() // sort for hash stability
        .ifEmpty { error("(${files.joinToString()} is empty") }
        .forEach { file ->
            file.inputStream().use { messageDigest.update(it, buffer) }
        }
}

fun MessageDigest.update(input: InputStream, buffer: ByteArray = ByteArray(BUFFER_SIZE)) {
    while (true) {
        val len = input.read(buffer).takeIf { it != -1 } ?: break
        update(buffer, 0, len)
    }
}

private fun File.flattenDir(): List<File> =
    when {
        isFile -> listOf(this)
        isDirectory -> listFiles().orEmpty().flatMap { it.flattenDir() }
        else -> error("'$this' doesn't exist")
    }
