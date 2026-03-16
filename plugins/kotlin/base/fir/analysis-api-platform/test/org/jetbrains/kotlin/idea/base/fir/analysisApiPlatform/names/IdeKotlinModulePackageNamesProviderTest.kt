// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.base.fir.analysisApiPlatform.names

import org.jetbrains.kotlin.analysis.api.projectStructure.KaLibraryModule
import org.jetbrains.kotlin.idea.base.analysisApiPlatform.KotlinModulePackageNamesProvider
import org.jetbrains.kotlin.idea.base.plugin.KotlinPluginMode
import org.jetbrains.kotlin.idea.base.projectStructure.toKaSourceModuleForProduction
import org.jetbrains.kotlin.idea.base.test.KotlinRoot
import org.jetbrains.kotlin.idea.test.AbstractMultiModuleTest
import java.io.File
import java.nio.file.Path
import kotlin.io.path.name

/**
 * Most of the crucial functionality of [IdeKotlinModulePackageNamesProvider][org.jetbrains.kotlin.idea.base.analysisApiPlatform.IdeKotlinModulePackageNamesProvider]
 * is tested by [KotlinBinaryRootToPackageIndexTest][org.jetbrains.kotlin.idea.base.indices.names.KotlinBinaryRootToPackageIndexTest]. We
 * don't want to duplicate these tests here, so we only test functionality which isn't covered by the index.
 *
 * The test is located in the FIR module instead of the base module. It's better to run the tests with the K2 plugin because the test uses
 * `KaModule` project structure, which has a better implementation in K2.
 *
 * @see org.jetbrains.kotlin.idea.base.indices.names.KotlinBinaryRootToPackageIndexTest
 */
class IdeKotlinModulePackageNamesProviderTest : AbstractMultiModuleTest() {
    override fun getTestDataDirectory(): File =
        KotlinRoot.DIR.resolve("base/fir/analysis-api-platform/testData/kotlinModulePackageNames")

    override val pluginMode: KotlinPluginMode
        get() = KotlinPluginMode.K2

    private val fooBarClassFilesPath = Path.of("classFiles", "fooBar")

    /**
     * This test data contains the same classes as [fooBarClassFilesPath], but the directory structure doesn't correspond to the package
     * structure.
     */
    private val fooBarMismatchedClassFilesPath = Path.of("classFiles", "fooBarMismatched")

    private val fooBarExpectedPackages = setOf("foo.bar", "foo.bar.car")

    private val monthsClassFilesPath = Path.of("classFiles", "months")

    private val monthsExpectedPackages = setOf(
        "months.april",
        "months.may",
        "months.javune.ghostMonth",
        "months.june",
        "months.july",
    )

    private val months2ClassFilesPath = Path.of("classFiles", "months2")

    private val months2ExpectedPackages = setOf(
        "months.january",
        "months.february",
        "months.march",
    )

    private val onlyJavaClassFilesPath = Path.of("classFiles", "onlyJava")

    private val onlyJavaExpectedPackages = emptySet<String>()

    private val allClassFilesPaths = listOf(
      fooBarClassFilesPath,
      fooBarMismatchedClassFilesPath,
      monthsClassFilesPath,
      months2ClassFilesPath,
      onlyJavaClassFilesPath,
    )

    fun testFooBarClassFiles() {
        checkPackageNamesInDirectory(
            listOf(fooBarClassFilesPath),
            fooBarExpectedPackages,
        )
    }

    fun testFooBarFlatClassFiles() {
        checkPackageNamesInDirectory(
            listOf(fooBarMismatchedClassFilesPath),
            fooBarExpectedPackages,
        )
    }

    fun testMonthsClassFiles() {
        checkPackageNamesInDirectory(
            listOf(monthsClassFilesPath),
            monthsExpectedPackages,
        )
    }

    fun testMonths2ClassFiles() {
        checkPackageNamesInDirectory(
            listOf(months2ClassFilesPath),
            months2ExpectedPackages,
        )
    }

    fun testOnlyJavaClassFiles() {
        checkPackageNamesInDirectory(
            listOf(onlyJavaClassFilesPath),
            onlyJavaExpectedPackages,
        )
    }

    fun testAllClassFiles() {
        checkPackageNamesInDirectory(
            allClassFilesPaths,
            buildSet {
                addAll(fooBarExpectedPackages)
                addAll(monthsExpectedPackages)
                addAll(months2ExpectedPackages)
                addAll(onlyJavaExpectedPackages)
            },
        )
    }

    private fun checkPackageNamesInDirectory(
      targetDirectories: List<Path>,
      expectedValues: Set<String>,
    ) {
        val libraryModules = setupLibraryModules(targetDirectories)

        val packageNamesProvider = KotlinModulePackageNamesProvider.getInstance(project)
        val values = buildSet {
            libraryModules.forEach { libraryModule ->
                val packageSet = packageNamesProvider.computePackageNames(libraryModule)
                assertNotNull("The package set must be computable.", packageSet)
                addAll(packageSet!!)
            }
        }

        assertEquals(expectedValues, values)
    }

    private fun setupLibraryModules(targetDirectories: List<Path>): List<KaLibraryModule> {
        val openapiModule = createMainModule()

        val targetBinaryRoots = targetDirectories.map { resolveBinaryRoot(it) }
        targetBinaryRoots.forEach { openapiModule.addLibrary(it, name = it.nameWithoutExtension) }

        val targetLibraryNames = targetBinaryRoots.mapTo(mutableSetOf()) { it.nameWithoutExtension }

        val sourceModule = openapiModule.toKaSourceModuleForProduction()
            ?: error("Cannot find source module for openapi module.")

        val libraryModules = sourceModule
            .directRegularDependencies
            .filterIsInstance<KaLibraryModule>()
            .filter { it.libraryName in targetLibraryNames }

        assertEquals("There is a mismatch between binary roots and library modules.", targetBinaryRoots.size, libraryModules.size)
        return libraryModules
    }

    private fun resolveBinaryRoot(path: Path): File {
        val binaryRootFile = getTestDataDirectory().resolve(path.toString())

        require(binaryRootFile.exists()) { "The binary root '${path.name}' does not exist." }
        return binaryRootFile
    }
}
