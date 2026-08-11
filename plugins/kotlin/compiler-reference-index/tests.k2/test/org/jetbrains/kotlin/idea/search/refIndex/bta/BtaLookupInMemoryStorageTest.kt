// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.search.refIndex.bta

import com.intellij.testFramework.rules.TempDirectory
import org.jetbrains.kotlin.buildtools.api.ExperimentalBuildToolsApi
import org.jetbrains.kotlin.buildtools.api.cri.CriToolchain
import org.jetbrains.kotlin.name.FqName
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.createFile
import kotlin.io.path.div
import kotlin.io.path.invariantSeparatorsPathString

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

    @Test
    fun `test create returns populated storage when CRI files contain valid data`() {
        val criRoot = createCriRoot()
        BtaTestFixtureSupport.copyFixtureInto(criRoot)

        assertNotNull(BtaLookupInMemoryStorage.create(criRoot, projectPath = "/proj"))
    }

    @Test
    fun `test get returns mapped paths for known FqName`() {
        val criRoot = createCriRoot()
        BtaTestFixtureSupport.copyFixtureInto(criRoot)
        val storage = createStorage(criRoot, projectPath = "/proj")

        val paths = storage[BtaTestFixtureSupport.animalFqName].map { it.invariantSeparatorsPathString }.toSet()

        val expected = BtaTestFixtureSupport.animalLookupPaths.map { "/proj/$it" }.toSet()
        assertEquals(expected, paths)
        assertFalse(paths.contains("/proj/${BtaTestFixtureSupport.unrelatedLookupPath}"))
    }

    @Test
    fun `test get returns empty list for unknown FqName`() {
        val criRoot = createCriRoot()
        BtaTestFixtureSupport.copyFixtureInto(criRoot)
        val storage = createStorage(criRoot, projectPath = "/proj")

        assertEquals(emptyList<Path>(), storage[FqName("does.not.Exist")])
    }

    @Test
    fun `test get resolves paths against project base directory`() {
        val criRoot = createCriRoot()
        BtaTestFixtureSupport.copyFixtureInto(criRoot)
        val storage = createStorage(criRoot, projectPath = "/abs/proj/base/../base")

        val paths = storage[BtaTestFixtureSupport.animalFqName].map { it.invariantSeparatorsPathString }.toSet()

        val expected = BtaTestFixtureSupport.animalLookupPaths.map { "/abs/proj/base/$it" }.toSet()
        assertEquals(expected, paths)
    }

    private fun createStorage(criRoot: Path, projectPath: String): BtaLookupInMemoryStorage {
        val storage = BtaLookupInMemoryStorage.create(criRoot, projectPath)
        assertNotNull(storage)
        return storage as BtaLookupInMemoryStorage
    }

    private fun createCriRoot(): Path = tempDir.newDirectoryPath(CriToolchain.DATA_PATH)
}
