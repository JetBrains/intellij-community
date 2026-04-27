// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.search.refIndex.bta

import com.intellij.testFramework.rules.TempDirectory
import org.jetbrains.kotlin.buildtools.api.ExperimentalBuildToolsApi
import org.jetbrains.kotlin.buildtools.api.cri.CriToolchain
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import java.nio.file.Path
import java.nio.file.attribute.FileTime
import kotlin.io.path.div
import kotlin.io.path.setLastModifiedTime

@OptIn(ExperimentalBuildToolsApi::class)
class BtaFileWatcherTest {
    @Rule
    @JvmField
    val tempDir = TempDirectory()

    @Test
    fun `test CRI artifact timestamp uses newest relevant file`() {
        val criPath = createCriPath()

        createCriArtifact(CriToolchain.LOOKUPS_FILENAME, timestamp = 10)
        createCriArtifact(CriToolchain.FILE_IDS_TO_PATHS_FILENAME, timestamp = 20)
        createCriArtifact(CriToolchain.SUBTYPES_FILENAME, timestamp = 30)

        assertEquals(FileTime.fromMillis(30), getCriArtifactTimestamp(criPath))
    }

    @Test
    fun `test CRI artifact timestamp is null when no tracked files exist`() {
        val criPath = createCriPath()

        assertNull(getCriArtifactTimestamp(criPath))
    }

    @Test
    fun `test CRI artifact timestamp ignores unrelated files`() {
        val criPath = createCriPath()

        createCriArtifact(CriToolchain.LOOKUPS_FILENAME, timestamp = 10)
        createCriArtifact("unrelated.table", timestamp = 30)

        assertEquals(FileTime.fromMillis(10), getCriArtifactTimestamp(criPath))
    }

    @Test
    fun `test CRI artifact timestamp returns null for nonexistent directory`() {
        val nonexistentPath = tempDir.newDirectoryPath("parent") / "nonexistent"

        assertNull(getCriArtifactTimestamp(nonexistentPath))
    }

    private fun createCriPath(): Path = tempDir.newDirectoryPath(CriToolchain.DATA_PATH)

    private fun createCriArtifact(fileName: String, timestamp: Long) {
        val artifactPath = tempDir.newFileNio("${CriToolchain.DATA_PATH}/$fileName")
        artifactPath.setLastModifiedTime(FileTime.fromMillis(timestamp))
    }
}
