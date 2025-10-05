// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.base.fir.projectStructure.scope

import com.intellij.openapi.module.Module
import org.jetbrains.kotlin.idea.base.projectStructure.scope.CombinedSourceAndClassRootsScope
import org.jetbrains.kotlin.idea.base.projectStructure.scope.getOrderedRoots
import org.jetbrains.kotlin.idea.test.projectStructureTest.TestContentRootKind
import org.jetbrains.kotlin.idea.test.projectStructureTest.TestProjectModule

abstract class AbstractCombinedSourceAndClassRootsScopeStructureTest : AbstractCombinedSourceAndClassRootsScopeTest() {
    override fun doTestWithScopes(
      combinedProductionScope: CombinedSourceAndClassRootsScope?,
      combinedTestScope: CombinedSourceAndClassRootsScope?,
      combinedLibraryScope: CombinedSourceAndClassRootsScope?,
      combinedLibrarySourcesScope: CombinedSourceAndClassRootsScope?,
      combinedScope: CombinedSourceAndClassRootsScope?,
    ) {
        val modulesWithProductionRoots = includedTestModules.toModuleSet()
        val modulesWithTestRoots = includedTestModules.toModuleSet()

        val productionRoots = includedTestModules.flatMap { it.contentRootVirtualFilesByKind(TestContentRootKind.PRODUCTION) }
        if (combinedProductionScope != null) {
            assertOrderedEquals("Invalid combined production scope roots", combinedProductionScope.getOrderedRoots(), productionRoots)
            assertEquals("Invalid combined production scope modules", modulesWithProductionRoots, combinedProductionScope.modules)
            assertFalse("The production sources scope should not be marked as including library class roots", combinedProductionScope.includesLibraryClassRoots)
            assertFalse("The production sources scope should not be marked as including library source roots", combinedProductionScope.includesLibrarySourceRoots)
        }

        val testRoots = includedTestModules.flatMap { it.contentRootVirtualFilesByKind(TestContentRootKind.TESTS) }
        if (combinedTestScope != null) {
            assertOrderedEquals("Invalid combined tests scope roots", combinedTestScope.getOrderedRoots(), testRoots)
            assertEquals("Invalid combined tests scope modules", modulesWithTestRoots, combinedTestScope.modules)
            assertFalse("The test sources scope should not be marked as including library class roots", combinedTestScope.includesLibraryClassRoots)
            assertFalse("The test sources scope should not be marked as including library source roots", combinedTestScope.includesLibrarySourceRoots)
        }

        val libraryRoots = includedTestLibraries.flatMap { it.toLibrary().classRoots }
        if (combinedLibraryScope != null) {
            assertOrderedEquals("Invalid combined library scope roots", combinedLibraryScope.getOrderedRoots(), libraryRoots)
            assertEquals("The library roots scope should not include any modules", emptySet<Module>(), combinedLibraryScope.modules)
            assertTrue("The library roots scope should be marked as including library class roots", combinedLibraryScope.includesLibraryClassRoots)
            assertFalse("The library roots scope should be marked as including library source roots", combinedLibraryScope.includesLibrarySourceRoots)
        }

        val librarySourcesRoots = includedTestLibraries.flatMap { it.toLibrary().sourceRoots }
        if (combinedLibrarySourcesScope != null) {
            assertOrderedEquals("Invalid combined library sources roots", combinedLibrarySourcesScope.getOrderedRoots(), librarySourcesRoots)
            assertEquals("The library sources roots scope should not include any modules", emptySet<Module>(), combinedLibrarySourcesScope.modules)
            assertFalse("The library sources roots scope should not be marked as including library class roots", combinedLibrarySourcesScope.includesLibraryClassRoots)
            assertTrue("The library sources roots scope should be marked as including library source roots", combinedLibrarySourcesScope.includesLibrarySourceRoots)
        }


        if (combinedScope != null) {
            val includesLibraryRoots = includedTestLibraries.isNotEmpty()
            val allRoots = productionRoots + testRoots + libraryRoots + librarySourcesRoots
            assertOrderedEquals("Invalid combined scope roots", combinedScope.getOrderedRoots(), allRoots)
            assertEquals("Invalid combined scope modules", modulesWithProductionRoots + modulesWithTestRoots, combinedScope.modules)
            assertEquals(
                "The combined scope should be marked as including library class roots if it combines at least one library",
                includesLibraryRoots,
                combinedScope.includesLibraryClassRoots,
            )
            assertEquals(
                "The combined scope should be marked as including library source roots if at least one included library has sources",
                includedTestLibraries.any { it.toLibrary().sourceRoots.isNotEmpty() },
                combinedScope.includesLibrarySourceRoots,
            )
        }
    }

    private fun List<TestProjectModule>.toModuleSet(): Set<Module> =
        map { it.toModule() }.toSet()
}
