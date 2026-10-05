// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.search.refIndex.bta

import com.intellij.testFramework.rules.TempDirectory
import org.jetbrains.kotlin.buildtools.api.ExperimentalBuildToolsApi
import org.jetbrains.kotlin.buildtools.api.cri.CriToolchain
import org.jetbrains.kotlin.idea.compiler.configuration.IdeKotlinVersion
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
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

    @Test
    fun `test Gradle CRI generation uses explicit property`() {
        assertTrue(isCriGenerationEnabled(property = "true", "2.4.20"))
        assertFalse(isCriGenerationEnabled(property = "false", "2.5.0"))
        assertTrue(isCriGenerationEnabled(property = "true"))
    }

    @Test
    fun `test Gradle CRI generation is enabled by default since KGP 2_5`() {
        assertFalse(isCriGenerationEnabled(property = null))
        assertFalse(isCriGenerationEnabled(property = null, "2.4.20"))
        assertTrue(isCriGenerationEnabled(property = null, "2.5.0-Beta2"))
        assertTrue(isCriGenerationEnabled(property = null, "2.6.0"))
        assertTrue(isCriGenerationEnabled(property = null, "2.4.20", "2.5.0"))
    }

    @Test
    fun `test Maven CRI generation uses explicit property`() {
        assertTrue(isMavenCriEnabled(property = "true", incremental = null, kotlinVersion = null))
        assertFalse(isMavenCriEnabled(property = "false", incremental = "true", kotlinVersion = "2.5.0"))
    }

    @Test
    fun `test Maven CRI generation is enabled by default since Kotlin 2_5 with incremental compilation`() {
        assertFalse(isMavenCriEnabled(property = null, incremental = "true", kotlinVersion = null))
        assertFalse(isMavenCriEnabled(property = null, incremental = "true", kotlinVersion = "2.4.20"))
        assertFalse(isMavenCriEnabled(property = null, incremental = null, kotlinVersion = "2.5.0"))
        assertFalse(isMavenCriEnabled(property = null, incremental = "false", kotlinVersion = "2.5.0"))
        assertTrue(isMavenCriEnabled(property = null, incremental = "true", kotlinVersion = "2.5.0-Beta2"))
        assertTrue(isMavenCriEnabled(property = null, incremental = "true", kotlinVersion = "2.6.0"))
    }

    private fun isCriGenerationEnabled(property: String?, vararg kgpVersions: String) = isGradleCriGenerationEnabled(
        property,
        kgpVersions.map(IdeKotlinVersion::get)
    )

    private fun isMavenCriEnabled(property: String?, incremental: String?, kotlinVersion: String?) = isMavenCriGenerationEnabled(
        property,
        incremental,
        kotlinVersion?.let(IdeKotlinVersion::get)
    )

    private fun createCriPath(): Path = tempDir.newDirectoryPath(CriToolchain.DATA_PATH)

    private fun createCriArtifact(fileName: String, timestamp: Long) {
        val artifactPath = tempDir.newFileNio("${CriToolchain.DATA_PATH}/$fileName")
        artifactPath.setLastModifiedTime(FileTime.fromMillis(timestamp))
    }
}
