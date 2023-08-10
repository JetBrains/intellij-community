// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.base.plugin.artifacts

import com.intellij.ide.caches.CachesInvalidator
import com.intellij.openapi.application.PathManager
import com.intellij.util.io.DigestUtil
import java.io.File
import java.security.MessageDigest

private val ROOT = File(PathManager.getSystemPath()).resolve("kotlin-lazy-file-pipeline-cache")
// 512Kb just because it's default in DigestUtil. For comparison, the biggest jar in kotlin dist is kotlin-compiler.jar is 158Mb
private const val BUFFER_SIZE = 512 * 1024

/**
 * - Re-calculates output when one of the inputs changes
 * - Re-calculates output when one of the outputs changes
 * - Supports "Invalidate caches"
 */
internal abstract class AbstractLazyFileOutputProducer<I : Any, C>(uniquePipelineId: String) : LazyFileOutputProducer<I, C> {
    private val hashFile = ROOT.resolve("$uniquePipelineId.hash")
    private val outputsFile = ROOT.resolve("$uniquePipelineId.outputs")

    private var outputs: List<File> = emptyList()
        get() = field.takeIf { it.isNotEmpty() } ?: readOutputsFile().also { field = it }
        set(value) {
            field = value.map { it.canonicalFile }
            outputsFile.parentFile.mkdirs()
            outputsFile.writeText(field.joinToString("\n"))
        }

    override fun lazyProduceOutput(input: I, computationContext: C): List<File> {
        if (!isUpToDate(input)) {
            outputs = produceOutput(input, computationContext).ifEmpty { return emptyList() }

            hashFile.parentFile.mkdirs()
            // "Commit the state" / "Commit transaction" (should be the last step for the algorithm to be fault-tolerant)
            hashFile.writeText(calculateMd5(input))
            check(isUpToDate(input)) { "It should be up-to-date after re-calculation" }
        }
        return outputs
    }

    fun getOutputIfUpToDateOrEmpty(input: I) = if (isUpToDate(input)) outputs else emptyList()

    override fun isUpToDate(input: I): Boolean = outputs.isNotEmpty() && outputs.all { it.exists() } && hashFile.isFile &&
            outputsFile.isFile && hashFile.readText().trim() == calculateMd5(input)

    protected abstract fun produceOutput(input: I, computationContext: C): List<File>
    protected abstract fun updateMessageDigestWithInput(messageDigest: MessageDigest, input: I, buffer: ByteArray)

    private fun readOutputsFile() = outputsFile.takeIf { it.exists() }
        ?.useLines { lines ->
            lines.map { File(it).canonicalFile }.toList()
        } ?: emptyList()

    private fun calculateMd5(input: I): String {
        val messageDigest = DigestUtil.md5()
        val buffer = ByteArray(BUFFER_SIZE)
        // hash outputsFile too to make sure that user doesn't change this file
        DigestUtil.updateContentHash(messageDigest, outputsFile.toPath(), buffer)
        updateMessageDigestWithInput(messageDigest, input, buffer)
        messageDigest.update(outputs, buffer)
        return DigestUtil.digestToHash(messageDigest)
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

fun MessageDigest.update(files: List<File>, buffer: ByteArray) {
    val messageDigest = this
    files.flatMap { it.flattenDir() }
        .map { it.canonicalFile }
        .sorted() // sort for hash stability
        .ifEmpty { error("(${files.joinToString()} is empty") }
        .forEach { file ->
            DigestUtil.updateContentHash(messageDigest, file.toPath(), buffer)
        }
}

private fun File.flattenDir(): List<File> =
    when {
        isFile -> listOf(this)
        isDirectory -> listFiles().orEmpty().flatMap { it.flattenDir() }
        else -> error("'$this' doesn't exist")
    }
