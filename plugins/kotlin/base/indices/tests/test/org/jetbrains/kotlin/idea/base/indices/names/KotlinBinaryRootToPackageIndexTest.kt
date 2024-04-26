// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.base.indices.names

import com.intellij.openapi.module.Module
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.util.indexing.FileBasedIndex
import org.jetbrains.kotlin.idea.base.test.KotlinRoot
import org.jetbrains.kotlin.idea.test.AbstractMultiModuleTest
import java.io.File
import java.nio.file.Path
import kotlin.io.path.name

/**
 * Checks that [KotlinBinaryRootToPackageIndex] returns the correct package sets contained in JAR and KLIB files.
 *
 * Because the index contains data from JARs/KLIBs, we needed some simple test JARs/KLIBs to feed to the tests. The JARs/KLIBs in the test
 * data are created from the zipped JPS project `binary-root-to-package-index-test-data-origin-project.zip`, also found in the test data.
 */
class KotlinBinaryRootToPackageIndexTest : AbstractMultiModuleTest() {
    override fun isFirPlugin(): Boolean = false

    override fun getTestDataDirectory(): File = KotlinRoot.DIR.resolve("base/indices/tests/testData/kotlinBinaryRootToPackageIndex")

    private val fooBarJarPath = Path.of("jars", "fooBar.jar")
    private val fooBarKlibPath = Path.of("klibs", "fooBar.klib")

    private val fooBarExpectedPackages = setOf("foo.bar", "foo.bar.car")

    private val monthsJarPath = Path.of("jars", "months.jar")
    private val monthsKlibPath = Path.of("klibs", "months.klib")

    private val monthsExpectedPackages = setOf(
        "months.april",
        "months.may",
        "months.javune.ghostMonth",
        "months.june",
        "months.july",
    )

    private val months2JarPath = Path.of("jars", "months2.jar")
    private val months2KlibPath = Path.of("klibs", "months2.klib")

    private val months2ExpectedPackages = setOf(
        "months.january",
        "months.february",
        "months.march",
    )

    private val onlyJavaJarPath = Path.of("jars", "onlyJava.jar")

    private val onlyJavaExpectedPackages = emptySet<String>()

    private val allJarPaths = listOf(fooBarJarPath, monthsJarPath, months2JarPath, onlyJavaJarPath)
    private val allKlibPaths = listOf(fooBarKlibPath, monthsKlibPath, months2KlibPath)

    fun testFooBarJar() {
        checkPackageNamesInJar(
            listOf(fooBarJarPath),
            fooBarExpectedPackages,
        )
    }

    fun testFooBarJarWithUnrelatedJars() {
        checkPackageNamesInJarWithUnrelatedLibraries(
            listOf(fooBarJarPath),
            fooBarExpectedPackages,
        )
    }

    fun testFooBarKlib() {
        checkPackageNamesInKlib(
            listOf(fooBarKlibPath),
            fooBarExpectedPackages,
        )
    }

    fun testFooBarKlibWithUnrelatedKlibs() {
        checkPackageNamesInKlibWithUnrelatedLibraries(
            listOf(fooBarKlibPath),
            fooBarExpectedPackages,
        )
    }

    fun testMonthsJar() {
        checkPackageNamesInJar(
            listOf(monthsJarPath),
            monthsExpectedPackages,
        )
    }

    fun testMonthsJarWithUnrelatedJars() {
        checkPackageNamesInJarWithUnrelatedLibraries(
            listOf(monthsJarPath),
            monthsExpectedPackages,
        )
    }

    fun testMonthsKlib() {
        checkPackageNamesInKlib(
            listOf(monthsKlibPath),
            monthsExpectedPackages,
        )
    }

    fun testMonthsKlibWithUnrelatedKlibs() {
        checkPackageNamesInKlibWithUnrelatedLibraries(
            listOf(monthsKlibPath),
            monthsExpectedPackages,
        )
    }

    fun testMonths2Jar() {
        checkPackageNamesInJar(
            listOf(months2JarPath),
            months2ExpectedPackages,
        )
    }

    fun testMonths2JarWithUnrelatedJars() {
        checkPackageNamesInJarWithUnrelatedLibraries(
            listOf(months2JarPath),
            months2ExpectedPackages,
        )
    }

    fun testMonths2Klib() {
        checkPackageNamesInKlib(
            listOf(months2KlibPath),
            months2ExpectedPackages,
        )
    }

    fun testMonths2KlibWithUnrelatedKlibs() {
        checkPackageNamesInKlibWithUnrelatedLibraries(
            listOf(months2KlibPath),
            months2ExpectedPackages,
        )
    }

    fun testOnlyJavaJar() {
        checkPackageNamesInJar(
            listOf(onlyJavaJarPath),
            onlyJavaExpectedPackages,
        )
    }

    fun testOnlyJavaJarWithUnrelatedJars() {
        checkPackageNamesInJarWithUnrelatedLibraries(
            listOf(onlyJavaJarPath),
            onlyJavaExpectedPackages,
        )
    }

    fun testAllJars() {
        checkPackageNamesInJar(
            allJarPaths,
            buildSet {
                addAll(fooBarExpectedPackages)
                addAll(monthsExpectedPackages)
                addAll(months2ExpectedPackages)
                addAll(onlyJavaExpectedPackages)
            },
        )
    }

    fun testAllKlibs() {
        checkPackageNamesInKlib(
            allKlibPaths,
            buildSet {
                addAll(fooBarExpectedPackages)
                addAll(monthsExpectedPackages)
                addAll(months2ExpectedPackages)
            },
        )
    }

    /**
     * If JARs share the same file name, their package names will be indexed under the same key.
     */
    fun testDuplicateJarNames() {
        val module = createMainModule()

        module.addLibraryFromPath(Path.of("duplicateJarNames", "months", "months.jar"))
        module.addLibraryFromPath(Path.of("duplicateJarNames", "months2", "months.jar"))

        val expectedValues = setOf(
            "months.april",
            "months.may",
            "months.javune.ghostMonth",
            "months.june",
            "months.july",
            "months.january",
            "months.february",
            "months.march",
        )

        val values = accessIndex("months.jar")

        assertEquals(expectedValues, values)
    }

    private fun checkPackageNamesInJar(targetLibraryPaths: List<Path>, expectedValues: Set<String>) {
        checkPackageNamesInLibrary(targetLibraryPaths, emptyList(), expectedValues)
    }

    private fun checkPackageNamesInJarWithUnrelatedLibraries(targetLibraryPaths: List<Path>, expectedValues: Set<String>) {
        checkPackageNamesInLibrary(targetLibraryPaths, allJarPaths - targetLibraryPaths, expectedValues)
    }

    private fun checkPackageNamesInKlib(targetLibraryPaths: List<Path>, expectedValues: Set<String>) {
        // Metadata in KLIBs contains parent packages, even if they don't contain any Kotlin code, so we have to allow false positives.
        checkPackageNamesInLibrary(targetLibraryPaths, emptyList(), expectedValues, allowFalsePositives = true)
    }

    private fun checkPackageNamesInKlibWithUnrelatedLibraries(targetLibraryPaths: List<Path>, expectedValues: Set<String>) {
        checkPackageNamesInLibrary(targetLibraryPaths, allKlibPaths - targetLibraryPaths, expectedValues, allowFalsePositives = true)
    }

    private fun checkPackageNamesInLibrary(
        targetLibraryPaths: List<Path>,
        unrelatedLibraryPaths: List<Path>,
        expectedValues: Set<String>,
        allowFalsePositives: Boolean = false,
    ) {
        val module = createMainModule()

        targetLibraryPaths.forEach { module.addLibraryFromPath(it) }
        unrelatedLibraryPaths.forEach { module.addLibraryFromPath(it) }

        val values = buildSet {
            targetLibraryPaths.forEach { jarPath ->
                addAll(accessIndex(jarPath.name))
            }
        }

        if (allowFalsePositives) {
            assertContainsElements(values, expectedValues)
        } else {
            assertEquals(expectedValues, values)
        }
    }

    private fun Module.addLibraryFromPath(libraryPath: Path) {
        val libraryFile = getTestDataDirectory().resolve(libraryPath.toString())
        require(libraryFile.exists()) { "The library file `$libraryPath` does not exist." }

        addLibrary(libraryFile, libraryPath.toString())
    }

    private fun accessIndex(rootName: String): Set<String> =
        FileBasedIndex.getInstance().getValues(KotlinBinaryRootToPackageIndex.NAME, rootName, GlobalSearchScope.allScope(project)).toSet()
}
