// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.search.refIndex.bta

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Path
import java.nio.file.attribute.FileTime
import java.util.concurrent.ConcurrentHashMap
import kotlin.io.path.Path

class BtaFileWatcherStateTest {
    // Placeholder paths — never read from disk, only used as map keys
    private val rootA = Path("/tmp/bta-test/a")
    private val rootB = Path("/tmp/bta-test/b")

    @Test
    fun `test no updates reported when timestamps unchanged`() {
        val lastSeen = ConcurrentHashMap(mapOf(rootA to FileTime.fromMillis(100)))

        val updated = lastSeen.computeUpdatedModules(
            modulesByPath = mapOf(rootA to setOf("module.main")),
            getTimestamp = { FileTime.fromMillis(100) },
        )

        assertTrue(updated.isEmpty())
        assertEquals(FileTime.fromMillis(100), lastSeen[rootA])
    }

    @Test
    fun `test all matching modules reported when timestamp advances`() {
        val lastSeen = ConcurrentHashMap(mapOf(rootA to FileTime.fromMillis(100)))

        val updated = lastSeen.computeUpdatedModules(
            modulesByPath = mapOf(rootA to setOf("module", "module.main", "module.test")),
            getTimestamp = { FileTime.fromMillis(200) },
        )

        assertEquals(setOf("module", "module.main", "module.test"), updated)
        assertEquals(FileTime.fromMillis(200), lastSeen[rootA])
    }

    @Test
    fun `test new CRI path with no prior baseline still reports updates`() {
        val lastSeen = ConcurrentHashMap<Path, FileTime>()

        val updated = lastSeen.computeUpdatedModules(
            modulesByPath = mapOf(rootA to setOf("module.main")),
            getTimestamp = { FileTime.fromMillis(50) },
        )

        assertEquals(setOf("module.main"), updated)
        assertEquals(FileTime.fromMillis(50), lastSeen[rootA])
    }

    @Test
    fun `test multiple roots updating in same poll merge module sets`() {
        val lastSeen = ConcurrentHashMap<Path, FileTime>()
        val timestamps = mapOf(
            rootA to FileTime.fromMillis(10),
            rootB to FileTime.fromMillis(20),
        )

        val updated = lastSeen.computeUpdatedModules(
            modulesByPath = mapOf(
                rootA to setOf("lib1", "lib1.main", "lib1.test"),
                rootB to setOf("lib2", "lib2.main", "lib2.test"),
            ),
            getTimestamp = timestamps::get,
        )

        assertEquals(setOf("lib1", "lib1.main", "lib1.test", "lib2", "lib2.main", "lib2.test"), updated)
    }

    @Test
    fun `test stale CRI paths are pruned from lastSeen`() {
        val lastSeen = ConcurrentHashMap(
            mapOf(
                rootA to FileTime.fromMillis(100),
                rootB to FileTime.fromMillis(200),
            )
        )

        lastSeen.computeUpdatedModules(
            modulesByPath = mapOf(rootA to setOf("module.main")),
            getTimestamp = { FileTime.fromMillis(100) },
        )

        assertTrue(lastSeen.containsKey(rootA))
        assertFalse(lastSeen.containsKey(rootB))
    }

    @Test
    fun `test missing timestamp (null) skips a path without crashing`() {
        val lastSeen = ConcurrentHashMap<Path, FileTime>()

        val updated = lastSeen.computeUpdatedModules(
            modulesByPath = mapOf(rootA to setOf("module.main")),
            getTimestamp = { null },
        )

        assertTrue(updated.isEmpty())
        assertFalse(lastSeen.containsKey(rootA))
    }
}
