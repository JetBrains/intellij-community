// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.search.refIndex.bta

import com.intellij.testFramework.rules.TempDirectory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.nio.file.Path
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

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

        val refreshedLookupStorages = refreshBtaStorageMap(
            currentCriRoots = listOf(rootA, rootB),
            updatedCriRoots = listOf(rootA),
            storagesByRoot = mapOf(rootA to lookupA, rootB to lookupB),
            createStorage = ::createLookupStorage,
        )
        val refreshedSubtypeStorages = refreshBtaStorageMap(
            currentCriRoots = listOf(rootA, rootB),
            updatedCriRoots = listOf(rootA),
            storagesByRoot = mapOf(rootA to subtypeA, rootB to subtypeB),
            createStorage = ::createSubtypeStorage,
        )

        assertNotSame(lookupA, refreshedLookupStorages[rootA])
        assertSame(lookupB, refreshedLookupStorages[rootB])
        assertNotSame(subtypeA, refreshedSubtypeStorages[rootA])
        assertSame(subtypeB, refreshedSubtypeStorages[rootB])
    }

    @Test
    fun `test refresh drops storage when CRI root disappears`() {
        val rootA = createCriRoot("rootA")
        val rootB = createCriRoot("rootB")

        val refreshedLookupStorages = refreshBtaStorageMap(
            currentCriRoots = listOf(rootA),
            updatedCriRoots = listOf(rootB),
            storagesByRoot = mapOf(rootA to createLookupStorage(rootA), rootB to createLookupStorage(rootB)),
            createStorage = ::createLookupStorage,
        )
        val refreshedSubtypeStorages = refreshBtaStorageMap(
            currentCriRoots = listOf(rootA),
            updatedCriRoots = listOf(rootB),
            storagesByRoot = mapOf(rootA to createSubtypeStorage(rootA), rootB to createSubtypeStorage(rootB)),
            createStorage = ::createSubtypeStorage,
        )

        assertEquals(setOf(rootA), refreshedLookupStorages.keys)
        assertEquals(setOf(rootA), refreshedSubtypeStorages.keys)
    }

    @Test
    fun `test refresh creates storage when CRI root appears`() {
        val rootA = createCriRoot("rootA")
        val rootB = createCriRoot("rootB")
        val lookupA = createLookupStorage(rootA)
        val subtypeA = createSubtypeStorage(rootA)

        val refreshedLookupStorages = refreshBtaStorageMap(
            currentCriRoots = listOf(rootA, rootB),
            updatedCriRoots = listOf(rootB),
            storagesByRoot = mapOf(rootA to lookupA),
            createStorage = ::createLookupStorage,
        )
        val refreshedSubtypeStorages = refreshBtaStorageMap(
            currentCriRoots = listOf(rootA, rootB),
            updatedCriRoots = listOf(rootB),
            storagesByRoot = mapOf(rootA to subtypeA),
            createStorage = ::createSubtypeStorage,
        )

        assertSame(lookupA, refreshedLookupStorages[rootA])
        assertNotNull(refreshedLookupStorages[rootB])
        assertSame(subtypeA, refreshedSubtypeStorages[rootA])
        assertNotNull(refreshedSubtypeStorages[rootB])
    }

    @Test
    fun `test refresh returns empty maps when all CRI roots disappear`() {
        val root = createCriRoot("root")

        val refreshedLookupStorages = refreshBtaStorageMap(
            currentCriRoots = emptyList(),
            updatedCriRoots = listOf(root),
            storagesByRoot = mapOf(root to createLookupStorage(root)),
            createStorage = ::createLookupStorage,
        )
        val refreshedSubtypeStorages = refreshBtaStorageMap(
            currentCriRoots = emptyList(),
            updatedCriRoots = listOf(root),
            storagesByRoot = mapOf(root to createSubtypeStorage(root)),
            createStorage = ::createSubtypeStorage,
        )

        assertTrue(refreshedLookupStorages.isEmpty())
        assertTrue(refreshedSubtypeStorages.isEmpty())
    }

    @Test
    fun `test refresh keeps one storage per duplicated current root`() {
        val root = createCriRoot("root")

        val refreshedLookupStorages = refreshBtaStorageMap(
            currentCriRoots = listOf(root, root),
            updatedCriRoots = listOf(root),
            storagesByRoot = emptyMap(),
            createStorage = ::createLookupStorage,
        )
        val refreshedSubtypeStorages = refreshBtaStorageMap(
            currentCriRoots = listOf(root, root),
            updatedCriRoots = listOf(root),
            storagesByRoot = emptyMap(),
            createStorage = ::createSubtypeStorage,
        )

        assertEquals(1, refreshedLookupStorages.size)
        assertEquals(1, refreshedSubtypeStorages.size)
    }

    @Test
    fun `test refresh does not reload unchanged root`() {
        val root = createCriRoot("root")
        val lookupStorage = createLookupStorage(root)
        val loadCount = AtomicInteger()

        val refreshedLookupStorages = refreshBtaStorageMap(
            currentCriRoots = listOf(root),
            updatedCriRoots = emptyList(),
            storagesByRoot = mapOf(root to lookupStorage),
            createStorage = {
                loadCount.incrementAndGet()
                createLookupStorage(it)
            },
        )

        assertEquals(0, loadCount.get())
        assertSame(lookupStorage, refreshedLookupStorages[root])
    }

    @Test
    fun `test create storage map overlaps root deserialization when parallelism is enabled`() {
        val rootA = createCriRoot("rootA")
        val rootB = createCriRoot("rootB")
        val startedDeserializations = CountDownLatch(2)
        val activeDeserializations = AtomicInteger()
        val maxActiveDeserializations = AtomicInteger()
        val createStorage: (Path) -> BtaLookupInMemoryStorage = { criRoot ->
            val currentlyActive = activeDeserializations.incrementAndGet()
            maxActiveDeserializations.updateAndGet { maxOf(it, currentlyActive) }
            startedDeserializations.countDown()
            try {
                assertTrue(
                    "Expected both CRI roots to start deserialization concurrently",
                    startedDeserializations.await(1, TimeUnit.SECONDS)
                )
                createLookupStorage(criRoot)
            } finally {
                activeDeserializations.decrementAndGet()
            }
        }

        val createdLookupStorages = createBtaStorageMap(
            criRoots = listOf(rootA, rootB),
            createStorage = createStorage,
            parallelism = 2,
        )

        assertEquals(setOf(rootA, rootB), createdLookupStorages.keys)
        assertTrue(maxActiveDeserializations.get() > 1)
    }

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
