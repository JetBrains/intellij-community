// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.base.fir.projectStructure.scope

import com.intellij.openapi.roots.OrderRootType
import com.intellij.openapi.roots.libraries.Library
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.search.GlobalSearchScopeUtil
import org.jetbrains.kotlin.analysis.api.projectStructure.KaModule
import org.jetbrains.kotlin.idea.base.projectStructure.KaSourceModuleKind
import org.jetbrains.kotlin.idea.base.projectStructure.scope.CombinableSourceAndClassRootsScope
import org.jetbrains.kotlin.idea.base.projectStructure.scope.CombinedSourceAndClassRootsScope
import org.jetbrains.kotlin.idea.base.projectStructure.scope.getOrderedRoots
import org.jetbrains.kotlin.idea.base.projectStructure.toKaLibraryModules
import org.jetbrains.kotlin.idea.base.projectStructure.toKaSourceModule
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
 * Note that project libraries should be part of at least one module dependency, as the workspace model might exclude them from indices
 * otherwise. See `UnloadableFileSetData`.
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
        combinedLibrarySourcesScope: CombinedSourceAndClassRootsScope?,
        combinedScope: CombinedSourceAndClassRootsScope?,
    )

    override fun getTestDataDirectory(): File =
        KotlinRoot.DIR.resolve("base").resolve("project-structure").resolve("testData").resolve("combinedSourceAndClassRootsScope")

    override fun doTestWithProjectStructure(testDirectory: String) {
        testModules.forEach { testProjectModule ->
            require(!testProjectModule.contentRoots.isNullOrEmpty()) {
                "Modules in combined scope tests should have explicitly specified content roots."
            }
        }

        val productionScopes = includedTestModules.mapNotNull { it.getModuleScope(KaSourceModuleKind.PRODUCTION) }
        val testScopes = includedTestModules.mapNotNull { it.getModuleScope(KaSourceModuleKind.TEST) }
        val libraryScopes = includedTestLibraries.map { it.getLibraryScope() }
        val librarySourcesScopes = includedTestLibraries.map { it.getLibrarySourceScope() }

        val combinedProductionScope = productionScopes.combine()
        val combinedTestScope = testScopes.combine()
        val combinedLibraryScope = libraryScopes.combine()
        val combinedLibrarySourcesScope = librarySourcesScopes.combine()

        val combinedScope = listOfNotNull(combinedProductionScope, combinedTestScope, combinedLibraryScope, combinedLibrarySourcesScope).combine()

        doTestWithScopes(
            combinedProductionScope = combinedProductionScope,
            combinedTestScope = combinedTestScope,
            combinedLibraryScope = combinedLibraryScope,
            combinedLibrarySourcesScope = combinedLibrarySourcesScope,
            combinedScope = combinedScope,
        )
    }

    internal val includedTestLibraries: List<TestProjectLibrary>
        get() = testProjectStructure.libraries.filterNot { it.name in testProjectStructure.excludedLibraries }

    internal val excludedTestLibraries: List<TestProjectLibrary>
        get() = testProjectStructure.libraries.filter { it.name in testProjectStructure.excludedLibraries }

    internal val includedTestModules: List<TestProjectModule>
        get() = testModules.filterNot { it.name in testProjectStructure.excludedModules }

    internal val includedTestModulesWithProductionRoots: List<TestProjectModule>
        get() = includedTestModules.filterByContentRootKind(TestContentRootKind.PRODUCTION)

    internal val includedTestModulesWithTestRoots: List<TestProjectModule>
        get() = includedTestModules.filterByContentRootKind(TestContentRootKind.TESTS)

    internal val excludedTestModules: List<TestProjectModule>
        get() = testModules.filter { it.name in testProjectStructure.excludedModules }

    internal val testModules: List<TestProjectModule>
        get() = testProjectStructure.modules

    internal fun List<CombinableSourceAndClassRootsScope>.combine(): CombinedSourceAndClassRootsScope? =
        CombinedSourceAndClassRootsScope.create(this, project) as? CombinedSourceAndClassRootsScope

    internal fun TestProjectModule.getModuleScope(
        sourceRootKind: KaSourceModuleKind,
    ): CombinableSourceAndClassRootsScope? {
        val ideaModule = toModule()
        val sourceModule = ideaModule.toKaSourceModule(sourceRootKind) ?: return null

        val expectedRoots = contentRootVirtualFilesByKind(sourceRootKind.toTestContentRootKind())

        return sourceModule.combinableContentScope.also { scope ->
            assertOrderedEquals(scope.getOrderedRoots(), expectedRoots)
        }
    }

    private fun KaSourceModuleKind.toTestContentRootKind() = when (this) {
        KaSourceModuleKind.PRODUCTION -> TestContentRootKind.PRODUCTION
        KaSourceModuleKind.TEST -> TestContentRootKind.TESTS
    }

    internal fun TestProjectLibrary.getLibraryScope(): CombinableSourceAndClassRootsScope {
        val library = toLibrary()
        val expectedRoots = library.classRoots

        require(expectedRoots.isNotEmpty()) { "The test library should have at least one class root." }

        val libraryModule = library.toKaLibraryModules(project).singleOrNull()
            ?: error("The test library should correspond to exactly one library module.")

        return libraryModule.combinableContentScope.also { scope ->
            assertOrderedEquals(scope.getOrderedRoots(), expectedRoots)
        }
    }

    internal fun TestProjectLibrary.getLibrarySourceScope(): CombinableSourceAndClassRootsScope {
        val library = toLibrary()
        val expectedRoots = library.sourceRoots

        require(expectedRoots.isNotEmpty()) { "The test library should have at least one source root." }

        val librarySourceModule = library.toKaLibraryModules(project).singleOrNull()?.librarySources
            ?: error("The test library should correspond to exactly one library module.")

        return librarySourceModule.combinableContentScope.also { scope ->
            assertOrderedEquals(scope.getOrderedRoots(), expectedRoots)
        }
    }

    internal val Library.classRoots: List<VirtualFile> get() = getFiles(OrderRootType.CLASSES).toList()
    internal val Library.sourceRoots: List<VirtualFile> get() = getFiles(OrderRootType.SOURCES).toList()

    private val KaModule.combinableContentScope: CombinableSourceAndClassRootsScope
        get() {
            // In the case of library modules, the combinable scope might be nested inside an intersection with a restriction scope. As we
            // only want to test combining roots scopes here and not scope merging in general, we can ignore the restriction scope.
            val flattenedScopes = GlobalSearchScopeUtil.flattenIntersectionScope(contentScope)

            return flattenedScopes.filterIsInstance<CombinableSourceAndClassRootsScope>().singleOrNull()
                ?: error("Expected a single combinable scope in the content scope of the `${this::class.simpleName}`.")
        }
}
