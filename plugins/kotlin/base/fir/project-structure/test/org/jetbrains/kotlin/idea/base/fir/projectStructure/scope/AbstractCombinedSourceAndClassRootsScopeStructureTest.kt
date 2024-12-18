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
        combinedScope: CombinedSourceAndClassRootsScope?,
    ) {
        val modulesWithProductionRoots = includedTestModulesWithProductionRoots.toModuleSet()
        val modulesWithTestRoots = includedTestModulesWithTestRoots.toModuleSet()

        val productionRoots = includedTestModules.flatMap { it.contentRootVirtualFilesByKind(TestContentRootKind.PRODUCTION) }
        if (combinedProductionScope != null) {
            assertOrderedEquals("Invalid combined production scope roots", combinedProductionScope.getOrderedRoots(), productionRoots)
            assertEquals("Invalid combined production scope modules", modulesWithProductionRoots, combinedProductionScope.modules)
            assertFalse("The production sources scope should not be marked as including library roots", combinedProductionScope.includesLibraryRoots)
        }

        val testRoots = includedTestModules.flatMap { it.contentRootVirtualFilesByKind(TestContentRootKind.TESTS) }
        if (combinedTestScope != null) {
            assertOrderedEquals("Invalid combined tests scope roots", combinedTestScope.getOrderedRoots(), testRoots)
            assertEquals("Invalid combined tests scope modules", modulesWithTestRoots, combinedTestScope.modules)
            assertFalse("The test sources scope should not be marked as including library roots", combinedTestScope.includesLibraryRoots)
        }

        val libraryRoots = includedTestLibraries.flatMap { it.toLibrary().classRoots }
        if (combinedLibraryScope != null) {
            assertOrderedEquals("Invalid combined library scope roots", combinedLibraryScope.getOrderedRoots(), libraryRoots)
            assertEquals("The library roots scope should not include any modules", emptySet<Module>(), combinedLibraryScope.modules)
            assertTrue("The library roots scope should be marked as including library roots", combinedLibraryScope.includesLibraryRoots)
        }

        if (combinedScope != null) {
            val includesLibraryRoots = includedTestLibraries.isNotEmpty()
            val allRoots = productionRoots + testRoots + libraryRoots
            assertOrderedEquals("Invalid combined scope roots", combinedScope.getOrderedRoots(), allRoots)
            assertEquals("Invalid combined scope modules", modulesWithProductionRoots + modulesWithTestRoots, combinedScope.modules)
            assertEquals(
                "The combined scope should be marked as including library roots if it combines at least one library",
                includesLibraryRoots,
                combinedScope.includesLibraryRoots,
            )
        }
    }

    private fun List<TestProjectModule>.toModuleSet(): Set<Module> =
        map { it.toModule() }.toSet()
}
