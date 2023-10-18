// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.base.indices.names

import com.intellij.openapi.module.Module
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.util.indexing.FileBasedIndex
import org.jetbrains.kotlin.idea.base.test.KotlinRoot
import org.jetbrains.kotlin.idea.stubs.AbstractMultiModuleTest
import java.io.File
import java.nio.file.Path
import kotlin.io.path.name

/**
 * Checks that [KotlinBinaryRootToPackageIndex] returns the correct package sets contained in JAR files.
 *
 * Because the index contains data from JARs, we needed some simple test JARs to feed to the tests. The JARs in the test data are created
 * from the zipped JPS project `binary-root-to-package-index-test-data-origin-project.zip`, also found in the test data.
 */
class KotlinBinaryRootToPackageIndexTest : AbstractMultiModuleTest() {
    override fun isFirPlugin(): Boolean = false

    override fun getTestDataDirectory(): File = KotlinRoot.DIR.resolve("base/indices-tests/testData/kotlinBinaryRootToPackageIndex")

    private val fooBarJarPath = Path.of("jars", "fooBar.jar")
    private val monthsJarPath = Path.of("jars", "months.jar")
    private val months2JarPath = Path.of("jars", "months2.jar")
    private val onlyJavaJarPath = Path.of("jars", "onlyJava.jar")

    fun testFooBarJar() {
        checkPackageNamesInJar(
            listOf(fooBarJarPath),
            emptyList(),
            setOf("foo.bar", "foo.bar.car"),
        )
    }

    fun testFooBarJarWithUnrelatedJars() {
        checkPackageNamesInJar(
            listOf(fooBarJarPath),
            listOf(monthsJarPath, months2JarPath),
            setOf("foo.bar", "foo.bar.car"),
        )
    }

    fun testMonthsJar() {
        checkPackageNamesInJar(
            listOf(monthsJarPath),
            emptyList(),
            setOf(
                "months.april",
                "months.may",
                "months.javune.ghostMonth",
                "months.june",
                "months.july",
            ),
        )
    }

    fun testMonthsJarWithUnrelatedJars() {
        checkPackageNamesInJar(
            listOf(monthsJarPath),
            listOf(fooBarJarPath, months2JarPath),
            setOf(
                "months.april",
                "months.may",
                "months.javune.ghostMonth",
                "months.june",
                "months.july",
            ),
        )
    }

    fun testMonths2Jar() {
        checkPackageNamesInJar(
            listOf(months2JarPath),
            emptyList(),
            setOf(
                "months.january",
                "months.february",
                "months.march",
            ),
        )
    }

    fun testMonths2JarWithUnrelatedJars() {
        checkPackageNamesInJar(
            listOf(months2JarPath),
            listOf(fooBarJarPath, monthsJarPath),
            setOf(
                "months.january",
                "months.february",
                "months.march",
            ),
        )
    }

    fun testOnlyJavaJar() {
        checkPackageNamesInJar(
            listOf(onlyJavaJarPath),
            emptyList(),
            emptySet(),
        )
    }

    fun testOnlyJavaJarWithUnrelatedJars() {
        checkPackageNamesInJar(
            listOf(onlyJavaJarPath),
            listOf(fooBarJarPath, monthsJarPath, months2JarPath),
            emptySet(),
        )
    }

    fun testMultipleJars() {
        checkPackageNamesInJar(
            listOf(
                fooBarJarPath,
                monthsJarPath,
                months2JarPath,
                onlyJavaJarPath
            ),
            emptyList(),
            setOf(
                "foo.bar",
                "foo.bar.car",
                "months.april",
                "months.may",
                "months.javune.ghostMonth",
                "months.june",
                "months.july",
                "months.january",
                "months.february",
                "months.march",
            ),
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

    private fun checkPackageNamesInJar(
        targetJarPaths: List<Path>,
        unrelatedJarPaths: List<Path>,
        expectedValues: Set<String>,
    ) {
        val module = createMainModule()

        targetJarPaths.forEach { module.addLibraryFromPath(it) }
        unrelatedJarPaths.forEach { module.addLibraryFromPath(it) }

        val values = buildSet {
            targetJarPaths.forEach { jarPath ->
                addAll(accessIndex(jarPath.name))
            }
        }

        assertEquals(expectedValues, values)
    }

    private fun Module.addLibraryFromPath(libraryPath: Path) {
        val libraryFile = getTestDataDirectory().resolve(libraryPath.toString())
        require(libraryFile.exists()) { "The library file `$libraryPath` does not exist." }

        addLibrary(libraryFile, libraryPath.toString())
    }

    private fun accessIndex(rootName: String): Set<String> =
        FileBasedIndex.getInstance().getValues(KotlinBinaryRootToPackageIndex.NAME, rootName, GlobalSearchScope.allScope(project)).toSet()
}
