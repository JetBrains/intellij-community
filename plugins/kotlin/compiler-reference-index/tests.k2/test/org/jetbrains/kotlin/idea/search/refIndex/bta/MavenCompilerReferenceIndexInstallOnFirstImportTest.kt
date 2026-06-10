// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.search.refIndex.bta

import kotlinx.coroutines.test.runTest
import org.junit.Test

class MavenCompilerReferenceIndexInstallOnFirstImportTest : AbstractMavenCompilerReferenceIndexTest() {
    @Test
    fun `test BTA file watcher is installed after Maven import when CRI generation property is enabled`() = runTest {
        val service = preloadCriService()
        assertFalse(service.isBtaFileWatcherInstalled)

        importProjectAsync(mavenProjectWithCriValue())

        assertTrue(service.isBtaFileWatcherInstalled)
    }

    @Test
    fun `test BTA file watcher is not installed after Maven import when CRI generation property is absent`() = runTest {
        val service = preloadCriService()
        assertFalse(service.isBtaFileWatcherInstalled)

        importProjectAsync(mavenProjectWithoutCri())

        assertFalse(service.isBtaFileWatcherInstalled)
    }

    @Test
    fun `test BTA file watcher is not installed after Maven import when CRI generation property is disabled`() = runTest {
        val service = preloadCriService()
        assertFalse(service.isBtaFileWatcherInstalled)

        importProjectAsync(mavenProjectWithCriValue("false"))

        assertFalse(service.isBtaFileWatcherInstalled)
    }
}
