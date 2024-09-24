// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.base.fir.projectStructure.scope

import org.jetbrains.kotlin.idea.base.projectStructure.scope.CombinedSourceAndClassRootsScope
import org.jetbrains.kotlin.idea.base.projectStructure.scope.getOrderedRoots
import org.jetbrains.kotlin.idea.test.projectStructureTest.TestContentRootKind

abstract class AbstractCombinedSourceAndClassRootsScopeStructureTest : AbstractCombinedSourceAndClassRootsScopeTest() {
    override fun doTestWithScopes(
        combinedProductionScope: CombinedSourceAndClassRootsScope?,
        combinedTestScope: CombinedSourceAndClassRootsScope?,
        combinedLibraryScope: CombinedSourceAndClassRootsScope?,
        combinedScope: CombinedSourceAndClassRootsScope?,
    ) {
        val modules = modulesByName.values.toSet()

        val productionRoots = testProjectStructure.modules.flatMap { it.contentRootVirtualFilesByKind(TestContentRootKind.PRODUCTION) }
        if (combinedProductionScope != null) {
            assertOrderedEquals("Invalid combined production scope roots", combinedProductionScope.getOrderedRoots(), productionRoots)
            assertEquals("Invalid combined production scope modules", modules, combinedProductionScope.modules)
            assertFalse("The production sources scope should not be marked as including library roots", combinedProductionScope.includesLibraryRoots)
        }

        val testRoots = testProjectStructure.modules.flatMap { it.contentRootVirtualFilesByKind(TestContentRootKind.TESTS) }
        if (combinedTestScope != null) {
            assertOrderedEquals("Invalid combined tests scope roots", combinedTestScope.getOrderedRoots(), testRoots)
            assertEquals("Invalid combined tests scope modules", modules, combinedTestScope.modules)
            assertFalse("The test sources scope should not be marked as including library roots", combinedTestScope.includesLibraryRoots)
        }

        val libraryRoots = testProjectStructure.libraries.flatMap { it.toLibrary().classRoots }
        if (combinedLibraryScope != null) {
            assertOrderedEquals("Invalid combined library scope roots", combinedLibraryScope.getOrderedRoots(), libraryRoots)
            assertEquals("The library roots scope should not include any modules", emptySet<Module>(), combinedLibraryScope.modules)
            assertTrue("The library roots scope should be marked as including library roots", combinedLibraryScope.includesLibraryRoots)
        }

        if (combinedScope != null) {
            val includesLibraryRoots = testProjectStructure.libraries.isNotEmpty()
            val allRoots = productionRoots + testRoots + libraryRoots
            assertOrderedEquals("Invalid combined scope roots", combinedScope.getOrderedRoots(), allRoots)
            assertEquals("Invalid combined scope modules", modules, combinedScope.modules)
            assertEquals(
                "The combined scope should be marked as including library roots if it combines at least one library",
                includesLibraryRoots,
                combinedScope.includesLibraryRoots,
            )
        }
    }
}
