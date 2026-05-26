// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.search.refIndex.bta

import com.intellij.testFramework.rules.TempDirectory
import org.jetbrains.kotlin.buildtools.api.ExperimentalBuildToolsApi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.nio.file.Path

@OptIn(ExperimentalBuildToolsApi::class)
class BtaKotlinCompilerReferenceIndexStorageRefreshTest {
    @Rule
    @JvmField
    val tempDir = TempDirectory()

    @Test
    fun `test refresh rebuilds changed roots and reuses unchanged roots`() {
        val rootA = createCriRoot("rootA")
        val rootB = createCriRoot("rootB")
        val lookupA = createLookupStorage(rootA)
        val lookupB = createLookupStorage(rootB)
        val subtypeA = createSubtypeStorage(rootA)
        val subtypeB = createSubtypeStorage(rootB)

        val refreshed = refresh(
            currentCriRoots = listOf(rootA, rootB),
            updatedCriRoots = listOf(rootA),
            lookupStoragesByRoot = mapOf(rootA to lookupA, rootB to lookupB),
            subtypeStoragesByRoot = mapOf(rootA to subtypeA, rootB to subtypeB),
        )

        assertNotSame(lookupA, refreshed.first[rootA])
        assertSame(lookupB, refreshed.first[rootB])
        assertNotSame(subtypeA, refreshed.second[rootA])
        assertSame(subtypeB, refreshed.second[rootB])
    }

    @Test
    fun `test refresh drops storage when CRI root disappears`() {
        val rootA = createCriRoot("rootA")
        val rootB = createCriRoot("rootB")

        val refreshed = refresh(
            currentCriRoots = listOf(rootA),
            updatedCriRoots = listOf(rootB),
            lookupStoragesByRoot = mapOf(rootA to createLookupStorage(rootA), rootB to createLookupStorage(rootB)),
            subtypeStoragesByRoot = mapOf(rootA to createSubtypeStorage(rootA), rootB to createSubtypeStorage(rootB)),
        )

        assertEquals(setOf(rootA), refreshed.first.keys)
        assertEquals(setOf(rootA), refreshed.second.keys)
    }

    @Test
    fun `test refresh creates storage when CRI root appears`() {
        val rootA = createCriRoot("rootA")
        val rootB = createCriRoot("rootB")
        val lookupA = createLookupStorage(rootA)
        val subtypeA = createSubtypeStorage(rootA)

        val refreshed = refresh(
            currentCriRoots = listOf(rootA, rootB),
            updatedCriRoots = listOf(rootB),
            lookupStoragesByRoot = mapOf(rootA to lookupA),
            subtypeStoragesByRoot = mapOf(rootA to subtypeA),
        )

        assertSame(lookupA, refreshed.first[rootA])
        assertNotNull(refreshed.first[rootB])
        assertSame(subtypeA, refreshed.second[rootA])
        assertNotNull(refreshed.second[rootB])
    }

    @Test
    fun `test refresh returns empty maps when all CRI roots disappear`() {
        val root = createCriRoot("root")

        val refreshed = refresh(
            currentCriRoots = emptyList(),
            updatedCriRoots = listOf(root),
            lookupStoragesByRoot = mapOf(root to createLookupStorage(root)),
            subtypeStoragesByRoot = mapOf(root to createSubtypeStorage(root)),
        )

        assertTrue(refreshed.first.isEmpty())
        assertTrue(refreshed.second.isEmpty())
    }

    @Test
    fun `test refresh keeps one storage per duplicated current root`() {
        val root = createCriRoot("root")

        val refreshed = refresh(
            currentCriRoots = listOf(root, root),
            updatedCriRoots = listOf(root),
            lookupStoragesByRoot = emptyMap(),
            subtypeStoragesByRoot = emptyMap(),
        )

        assertEquals(1, refreshed.first.size)
        assertEquals(1, refreshed.second.size)
    }

    private fun refresh(
        currentCriRoots: Collection<Path>,
        updatedCriRoots: Collection<Path>,
        lookupStoragesByRoot: Map<Path, BtaLookupInMemoryStorage>,
        subtypeStoragesByRoot: Map<Path, BtaSubtypeInMemoryStorage>,
    ): Pair<Map<Path, BtaLookupInMemoryStorage>, Map<Path, BtaSubtypeInMemoryStorage>> = refreshBtaStorageMaps(
        currentCriRoots = currentCriRoots,
        updatedCriRoots = updatedCriRoots,
        lookupStoragesByRoot = lookupStoragesByRoot,
        subtypeStoragesByRoot = subtypeStoragesByRoot,
        createLookupStorage = ::createLookupStorage,
        createSubtypeStorage = ::createSubtypeStorage,
    )

    private fun createCriRoot(name: String): Path = tempDir.newDirectoryPath(name)
        .also(BtaTestFixtureSupport::copyFixtureInto)

    private fun createLookupStorage(criRoot: Path): BtaLookupInMemoryStorage {
        val storage = BtaLookupInMemoryStorage.create(criRoot, projectPath = tempDir.rootPath.toString())
        assertNotNull(storage)
        return storage as BtaLookupInMemoryStorage
    }

    private fun createSubtypeStorage(criRoot: Path): BtaSubtypeInMemoryStorage {
        val storage = BtaSubtypeInMemoryStorage.create(criRoot)
        assertNotNull(storage)
        return storage as BtaSubtypeInMemoryStorage
    }
}
