// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.base.fir.projectStructure.scope

import com.intellij.openapi.roots.OrderRootType
import com.intellij.openapi.roots.libraries.Library
import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.kotlin.idea.base.projectStructure.moduleInfo.LibraryWithoutSourceScope
import org.jetbrains.kotlin.idea.base.projectStructure.scope.CombinableSourceAndClassRootsScope
import org.jetbrains.kotlin.idea.base.projectStructure.scope.CombinedSourceAndClassRootsScope
import org.jetbrains.kotlin.idea.base.projectStructure.scope.ModuleSourcesScope
import org.jetbrains.kotlin.idea.base.projectStructure.scope.getOrderedRoots
import org.jetbrains.kotlin.idea.base.test.KotlinRoot
import org.jetbrains.kotlin.idea.test.projectStructureTest.AbstractProjectStructureTest
import org.jetbrains.kotlin.idea.test.projectStructureTest.TestContentRootKind
import org.jetbrains.kotlin.idea.test.projectStructureTest.TestProjectLibrary
import org.jetbrains.kotlin.idea.test.projectStructureTest.TestProjectModule
import java.io.File

/**
 * A test data-based test for [org.jetbrains.kotlin.idea.base.projectStructure.scope.CombinedSourceAndClassRootsScope].
 *
 * From a test project structure with modules and libraries, the test creates combined production, test, and library scopes. Individual test
 * implementations can then test these already created scopes, for example to assert that combined roots are correct and in the correct
 * order.
 *
 * The test is part of `kotlin.base.fir.project-structure` instead of `kotlin.base.project-structure` because tests need to be executed
 * either with the K1 or K2 Kotlin plugin, and the base module assumes neither.
 */
abstract class AbstractCombinedSourceAndClassRootsScopeTest : AbstractProjectStructureTest<ScopesTestProjectStructure>(
    ScopesTestProjectStructureParser
) {
    /**
     * Executes the test with scopes constructed from the project structure.
     *
     * Some scopes may be `null` in case no modules/libraries are part of the project structure.
     */
    protected abstract fun doTestWithScopes(
        combinedProductionScope: CombinedSourceAndClassRootsScope?,
        combinedTestScope: CombinedSourceAndClassRootsScope?,
        combinedLibraryScope: CombinedSourceAndClassRootsScope?,
        combinedScope: CombinedSourceAndClassRootsScope?,
    )

    override fun getTestDataDirectory(): File =
        KotlinRoot.DIR.resolve("base").resolve("project-structure").resolve("testData").resolve("combinedSourceAndClassRootsScope")

    override fun doTestWithProjectStructure(testDirectory: String) {
        testProjectStructure.modules.forEach { testProjectModule ->
            require(testProjectModule.contentRoots.isNotEmpty()) {
                "Modules in combined scope tests should have explicitly specified content roots."
            }
        }

        val productionScopes = includedTestModules.map { it.createModuleScope(ModuleSourcesScope.SourceRootKind.PRODUCTION) }
        val testScopes = includedTestModules.map { it.createModuleScope(ModuleSourcesScope.SourceRootKind.TESTS) }
        val libraryScopes = includedTestLibraries.map { it.createLibraryScope() }

        val combinedProductionScope = productionScopes.combine()
        val combinedTestScope = testScopes.combine()
        val combinedLibraryScope = libraryScopes.combine()

        val combinedScope = listOfNotNull(combinedProductionScope, combinedTestScope, combinedLibraryScope).combine()

        doTestWithScopes(
            combinedProductionScope,
            combinedTestScope,
            combinedLibraryScope,
            combinedScope,
        )
    }

    internal val includedTestLibraries: List<TestProjectLibrary>
        get() = testProjectStructure.libraries.filterNot { it.name in testProjectStructure.excludedLibraries }

    internal val includedTestModules: List<TestProjectModule>
        get() = testProjectStructure.modules.filterNot { it.name in testProjectStructure.excludedModules }

    internal fun List<CombinableSourceAndClassRootsScope>.combine(): CombinedSourceAndClassRootsScope? =
        CombinedSourceAndClassRootsScope.create(this, project) as? CombinedSourceAndClassRootsScope

    internal fun TestProjectModule.createModuleScope(
        sourceRootKind: ModuleSourcesScope.SourceRootKind,
    ): ModuleSourcesScope {
        val module = toModule()
        val expectedRoots = contentRootVirtualFilesByKind(sourceRootKind.toTestContentRootKind())

        return ModuleSourcesScope(module, sourceRootKind).also { scope ->
            assertOrderedEquals(scope.getOrderedRoots(), expectedRoots)
        }
    }

    private fun ModuleSourcesScope.SourceRootKind.toTestContentRootKind() = when (this) {
        ModuleSourcesScope.SourceRootKind.PRODUCTION -> TestContentRootKind.PRODUCTION
        ModuleSourcesScope.SourceRootKind.TESTS -> TestContentRootKind.TESTS
    }

    internal fun TestProjectLibrary.createLibraryScope(): LibraryWithoutSourceScope {
        val library = toLibrary()
        val expectedRoots = library.classRoots

        require(expectedRoots.isNotEmpty()) { "The test library should have at least one class root." }

        return LibraryWithoutSourceScope(project, null, null, library).also { scope ->
            assertOrderedEquals(scope.getOrderedRoots(), expectedRoots)
        }
    }

    internal val Library.classRoots: List<VirtualFile> get() = getFiles(OrderRootType.CLASSES).toList()
}
