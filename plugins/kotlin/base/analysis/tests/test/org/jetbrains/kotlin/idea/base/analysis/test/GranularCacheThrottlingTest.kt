// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.base.analysis.test

import com.intellij.openapi.util.LowMemoryWatcher
import com.intellij.openapi.util.registry.Registry
import org.jetbrains.kotlin.idea.base.analysis.LibraryDependenciesCacheImpl
import org.jetbrains.kotlin.idea.base.plugin.artifacts.TestKotlinArtifacts
import org.jetbrains.kotlin.idea.base.projectStructure.LibraryDependenciesCache
import org.jetbrains.kotlin.idea.base.projectStructure.LibraryInfoCache
import org.jetbrains.kotlin.idea.base.projectStructure.moduleInfo.LibraryInfo
import org.jetbrains.kotlin.idea.base.util.K1ModeProjectStructureApi
import org.jetbrains.kotlin.idea.test.addDependency
import org.jetbrains.kotlin.test.util.jarRoot
import org.jetbrains.kotlin.test.util.projectLibrary

@OptIn(K1ModeProjectStructureApi::class)
class GranularCacheThrottlingTest : AbstractModuleInfoCacheTest() {
    fun testRepetitiveInvalidationsAreThrottled() {
        val (library1, library2) = setUpProjectWithLibraries()
        // cold run: low-memory signal leads to cache invalidation
        checkCacheAfterLowMemorySignal(library1, library2, expectedCountAfterSignal = 0)
        // the second signal happens immediately after the first: invalidation is throttled
        checkCacheAfterLowMemorySignal(library1, library2, expectedCountAfterSignal = 2)
    }

    fun testDistantInvalidationsSucceed() {
        val throttlingTimeoutMs = Registry.intValue("kotlin.caches.fine.grained.throttling.timeout.ms")
        val (library1, library2) = setUpProjectWithLibraries()
        // cold run: low-memory signal leads to cache invalidation
        checkCacheAfterLowMemorySignal(library1, library2, expectedCountAfterSignal = 0)

        Thread.sleep(throttlingTimeoutMs.toLong() + 500)

        // the second signal happens after the necessary timeout: invalidation succeeds
        checkCacheAfterLowMemorySignal(library1, library2, expectedCountAfterSignal = 0)
    }

    private fun checkCacheAfterLowMemorySignal(firstLibrary: LibraryInfo, secondLibrary: LibraryInfo, expectedCountAfterSignal: Int) {
        firstLibrary.dependencies()
        secondLibrary.dependencies()

        assertEquals(2, getCacheContent().entries.count())
        LowMemoryWatcher.onLowMemorySignalReceived(true)
        assertEquals(expectedCountAfterSignal, getCacheContent().entries.count())
    }

    private fun setUpProjectWithLibraries(): Pair<LibraryInfo, LibraryInfo> {
        val module = createModule("module") {
            addSourceFolder("src", isTest = false)
            addSourceFolder("test", isTest = true)
        }

        val library1 = projectLibrary("l1", TestKotlinArtifacts.kotlinDaemon.jarRoot)
        val library2 = projectLibrary("l2", TestKotlinArtifacts.kotlinCompiler.jarRoot)

        module.addDependency(library1)
        module.addDependency(library2)

        val l1 = LibraryInfoCache.getInstance(project)[library1].single()
        val l2 = LibraryInfoCache.getInstance(project)[library2].single()

        return l1 to l2
    }

    private fun getCacheContent(): Map<LibraryInfo, LibraryDependenciesCache.LibraryDependencies> {
        return (LibraryDependenciesCache.getInstance(project) as? LibraryDependenciesCacheImpl)?.getCacheContentForTests()
            ?: error("Unexpected implementation of ${LibraryDependenciesCache::class.java.simpleName}, " +
                             "expected ${LibraryDependenciesCacheImpl::class.java.simpleName}. Please update the test.")
    }
}
