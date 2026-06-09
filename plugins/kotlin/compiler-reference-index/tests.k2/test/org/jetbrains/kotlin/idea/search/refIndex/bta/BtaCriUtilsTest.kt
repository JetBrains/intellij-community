// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.search.refIndex.bta

import com.intellij.testFramework.rules.TempDirectory
import org.jetbrains.kotlin.buildtools.api.ExperimentalBuildToolsApi
import org.jetbrains.kotlin.buildtools.api.cri.CriToolchain
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.div

@OptIn(ExperimentalBuildToolsApi::class)
class BtaCriUtilsTest {
    @Rule
    @JvmField
    val tempDir = TempDirectory()

    @Test
    fun `test CRI paths include all compilation directories`() {
        val modulePath = createModulePath()
        val expectedCriRoots = listOf(
            createGradleCriRoot(modulePath, "compileCommonMainKotlinMetadata"),
            createGradleCriRoot(modulePath, "compileKotlinJvm"),
            createGradleCriRoot(modulePath, "compileTestKotlinJvm"),
        )

        createGradleKotlinPath(modulePath, "sessions")
        createGradleKotlinPath(modulePath, "compileJsMainKotlinMetadata", "cacheable")

        assertEquals(expectedCriRoots.map { it.toString() }.sorted(), getGradleCriPaths(modulePath).map { it.toString() }.sorted())
    }

    @Test
    fun `test CRI paths ignore modules without CRI artifacts`() {
        val modulePath = createModulePath()

        createGradleKotlinPath(modulePath, "compileKotlinJvm", "cacheable")
        createGradleKotlinPath(modulePath, "compileMetadata")

        assertEquals(emptyList<String>(), getGradleCriPaths(modulePath).map { it.toString() })
    }

    @Test
    fun `test CRI paths return empty when build kotlin directory does not exist`() {
        val modulePath = createModulePath()

        assertEquals(emptyList<String>(), getGradleCriPaths(modulePath).map { it.toString() })
    }

    @Test
    fun `test CRI paths return empty when build kotlin directory is empty`() {
        val modulePath = createModulePath()
        (modulePath / "build" / "kotlin").createDirectories()

        assertEquals(emptyList<String>(), getGradleCriPaths(modulePath).map { it.toString() })
    }

    @Test
    fun `test Maven CRI path found when artifacts exist`() {
        val modulePath = createModulePath()
        val expectedCriPath = (modulePath / "target" / "kotlin-ic" / "compile" / CriToolchain.DATA_PATH).createDirectories()

        assertEquals(expectedCriPath.toString(), getMavenCriPath(modulePath)?.toString())
    }

    @Test
    fun `test Maven CRI path returns null when target directory does not exist`() {
        val modulePath = createModulePath()

        assertNull(getMavenCriPath(modulePath))
    }

    private fun createModulePath(): Path = tempDir.newDirectoryPath("module")

    private fun createGradleCriRoot(modulePath: Path, taskName: String): Path =
        createGradleKotlinPath(modulePath, taskName, "cacheable", "cri")

    private fun createGradleKotlinPath(modulePath: Path, vararg relativeSegments: String): Path =
        relativeSegments.fold(modulePath / "build" / "kotlin") { path, segment -> path / segment }.createDirectories()
}
