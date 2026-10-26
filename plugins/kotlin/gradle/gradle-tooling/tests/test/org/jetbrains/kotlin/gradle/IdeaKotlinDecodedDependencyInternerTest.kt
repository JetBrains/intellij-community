// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.gradle

import org.jetbrains.kotlin.idea.gradleTooling.IdeaKotlinDecodedDependencyInterner
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotSame
import kotlin.test.assertNull
import kotlin.test.assertSame

class IdeaKotlinDecodedDependencyInternerTest {

    @Test
    fun givesOneInstanceForEqualContent() {
        val interner = IdeaKotlinDecodedDependencyInterner<Decoded>()
        var decodeCount = 0
        val decode = { bytes: ByteArray -> decodeCount++; Decoded(bytes.first()) }

        val first = interner.intern(bytes(1), decode)
        val second = interner.intern(bytes(1), decode)

        assertSame(first, second, "Expected one instance for two arrays with equal content")
        assertEquals(1, decodeCount, "Expected one decode for equal content")
    }

    @Test
    fun givesDifferentInstancesForDifferentContent() {
        val interner = IdeaKotlinDecodedDependencyInterner<Decoded>()

        val first = interner.intern(bytes(1)) { Decoded(it.first()) }
        val second = interner.intern(bytes(2)) { Decoded(it.first()) }

        assertNotSame(first, second)
        assertEquals(Decoded(2), second)
    }

    @Test
    fun doesNotKeepFailedDecodes() {
        val interner = IdeaKotlinDecodedDependencyInterner<Decoded>()

        assertNull(interner.intern(bytes(1)) { null })
        assertEquals(Decoded(1), interner.intern(bytes(1)) { Decoded(it.first()) })
    }

    private data class Decoded(val seed: Byte)

    private fun bytes(seed: Int): ByteArray = ByteArray(16) { seed.toByte() }
}
