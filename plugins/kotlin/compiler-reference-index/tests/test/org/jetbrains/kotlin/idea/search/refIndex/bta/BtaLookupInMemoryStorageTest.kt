// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.search.refIndex.bta

import com.intellij.testFramework.rules.TempDirectory
import org.jetbrains.kotlin.buildtools.api.ExperimentalBuildToolsApi
import org.jetbrains.kotlin.buildtools.api.cri.CriToolchain
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.createFile
import kotlin.io.path.div

@OptIn(ExperimentalBuildToolsApi::class)
class BtaLookupInMemoryStorageTest {
    @Rule
    @JvmField
    val tempDir = TempDirectory()

    @Test
    fun `test hasLookupData returns true when both files exist`() {
        val criRoot = createCriRoot()
        (criRoot / CriToolchain.LOOKUPS_FILENAME).createFile()
        (criRoot / CriToolchain.FILE_IDS_TO_PATHS_FILENAME).createFile()

        assertTrue(criRoot.hasLookupData())
    }

    @Test
    fun `test hasLookupData returns false when lookups file missing`() {
        val criRoot = createCriRoot()
        (criRoot / CriToolchain.FILE_IDS_TO_PATHS_FILENAME).createFile()

        assertFalse(criRoot.hasLookupData())
    }

    @Test
    fun `test hasLookupData returns false when file IDs file missing`() {
        val criRoot = createCriRoot()
        (criRoot / CriToolchain.LOOKUPS_FILENAME).createFile()

        assertFalse(criRoot.hasLookupData())
    }

    @Test
    fun `test hasLookupData returns false when directory is empty`() {
        val criRoot = createCriRoot()

        assertFalse(criRoot.hasLookupData())
    }

    @Test
    fun `test create returns null when CRI root has no lookup data`() {
        val criRoot = createCriRoot()

        assertNull(BtaLookupInMemoryStorage.create(criRoot, projectPath = criRoot.toString()))
    }

    @Test
    fun `test create returns null when reading lookups data throws IOException`() {
        val criRoot = createCriRoot()
        (criRoot / CriToolchain.LOOKUPS_FILENAME).createDirectories()
        (criRoot / CriToolchain.FILE_IDS_TO_PATHS_FILENAME).createFile()

        assertNull(BtaLookupInMemoryStorage.create(criRoot, projectPath = criRoot.toString()))
    }

    private fun createCriRoot(): Path = tempDir.newDirectoryPath(CriToolchain.DATA_PATH)
}
