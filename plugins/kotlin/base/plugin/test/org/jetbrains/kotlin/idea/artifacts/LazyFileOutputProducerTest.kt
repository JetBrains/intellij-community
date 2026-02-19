// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.artifacts

import com.intellij.util.io.DigestUtil
import junit.framework.TestCase
import org.jetbrains.kotlin.idea.base.plugin.artifacts.AbstractLazyFileOutputProducer
import java.io.File
import java.security.MessageDigest
import kotlin.io.path.createTempDirectory

private const val TEST_ID = "test-id"

class LazyFileOutputProducerTest : TestCase() {
    private lateinit var tempDir: File
    private val producer by lazy { TestFileOutputProducer(tempDir.resolve("output")) }

    override fun setUp() {
        super.setUp()
        tempDir = createTempDirectory().toFile()
        AbstractLazyFileOutputProducer.invalidateCaches()
    }

    override fun tearDown() {
        try {
            tempDir.deleteRecursively()
            AbstractLazyFileOutputProducer.invalidateCaches()
        } finally {
            super.tearDown()
        }
    }

    fun `test simple upToDate`() {
        assertFalse("Not yet expected to be up-to-date", producer.isUpToDate("foo"))
        val output = producer.lazyTestOutput("foo")
        checkOutput(output)
        assertTrue("Expected to be up-to-date", producer.isUpToDate("foo"))
    }

    fun `test recalculate when input changes`() {
        assertFalse("Not yet expected to be up-to-date", producer.isUpToDate("foo"))
        val output = producer.lazyTestOutput("foo")
        checkOutput(output)
        assertTrue("Expected to be up-to-date", producer.isUpToDate("foo"))
        // Not up-to-date with changed input
        assertFalse("Input changed so not expected to be up-to-date", producer.isUpToDate("bar"))
    }

    fun `test recalculate when output changes`() {
        assertFalse("Not yet expected to be up-to-date", producer.isUpToDate("foo"))
        val output = producer.lazyTestOutput("foo")
        checkOutput(output)
        assertTrue("Expected to be up-to-date", producer.isUpToDate("foo"))
        // Manually change output
        output.writeText("bar")
        assertFalse("Output changed so not expected to be up-to-date", producer.isUpToDate("foo"))
    }

    private fun checkOutput(output: File) {
        assertTrue("$output should exist", output.exists())
        assertEquals("foo", output.readText())
    }
}

private class TestFileOutputProducer(private val output: File) : AbstractLazyFileOutputProducer<String, Unit>(TEST_ID) {
    override fun produceOutput(input: String, computationContext: Unit): List<File> {
        output.writeText(input)
        return listOf(output)
    }

    fun lazyTestOutput(input: String) = lazyProduceOutput(input, Unit).singleOrNull()
        ?: error("${TestFileOutputProducer::produceOutput.name} returns single file")

    override fun updateMessageDigestWithInput(messageDigest: MessageDigest, input: String, buffer: ByteArray) {
        input.byteInputStream().use { DigestUtil.updateContentHash(messageDigest, it, buffer) }
    }
}
