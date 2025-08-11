// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.base.indices.names

import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.openapi.vfs.impl.jrt.JrtFileSystemImpl
import com.intellij.openapi.vfs.jrt.JrtFileSystem
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.testFramework.common.runAll
import com.intellij.testFramework.utils.io.createDirectory
import com.intellij.util.indexing.FileBasedIndex
import org.jetbrains.kotlin.idea.base.test.KotlinRoot
import org.jetbrains.kotlin.idea.test.AbstractMultiModuleTest
import org.junit.Ignore
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import java.io.File
import java.nio.file.Files
import java.nio.file.Path

/**
 * Checks that [KotlinBinaryRootToPackageIndex] correctly indexes `.class` files contained in JRT file systems.
 *
 * The test sets up a custom JRT file system from a minimal JRT image which contains only the `kotlin.stdlib` module (plus its dependencies)
 * and a custom module `customRuntime`, which contains two Kotlin packages and a Java-only package. The JRT class root is added as a library
 * root, which is sufficient to get the platform to index the JRT image properly. We can thus avoid having to set up a custom JRT-based SDK.
 *
 * The source for the `customRuntime` module can be found in `binary-root-to-package-index-test-data-origin-project.zip`, together with a
 * guide on how to create the JRT image using `jlink`.
 */
@RunWith(JUnit4::class)
class KotlinBinaryRootToPackageIndexJrtTest : AbstractMultiModuleTest() {
    private val testDataPath = KotlinRoot.DIR.resolve("base/indices/tests/testData/kotlinBinaryRootToPackageIndex/jrt").toPath()

    lateinit var jrtPath: Path
    lateinit var jrtRoot: VirtualFile

    override fun getTestDataDirectory(): File = testDataPath.toFile()

    override fun setUp() {
        super.setUp()
        setupJrtFileSystem()
    }

    override fun tearDown() {
        runAll(
            { releaseJrtFileSystem() },
            { super.tearDown() },
        )
    }

    @Test
    @Ignore("KTIJ-35195")
    fun testCustomRuntime() {
        val module = createMainModule()
        module.addLibrary(jrtRoot, "JRT-based library")

        // The custom Java module in the JRT image is called `customRuntime`.
        val values = FileBasedIndex.getInstance().getValues(
            KotlinBinaryRootToPackageIndex.NAME,
            "customRuntime",
            GlobalSearchScope.allScope(project),
        ).toSet()

        assertEquals(
            setOf("foo.kotlinClass", "foo.kotlinCallable"),
            values,
        )
    }

    private fun setupJrtFileSystem() {
        jrtPath = Path.of(FileUtil.getTempDirectory()).createDirectory("jrt")

        Files.createDirectories(jrtPath)
        Files.writeString(jrtPath.resolve("release"), "JAVA_VERSION=9\n")

        val libDir = Files.createDirectory(jrtPath.resolve("lib"))

        // `jrt-fs.jar` is required for the JRT file system to work.
        Files.copy(testDataPath.resolve("jrt-fs.jar"), libDir.resolve("jrt-fs.jar"))
        Files.copy(testDataPath.resolve("modules"), libDir.resolve("modules"))

        val jrtDir = LocalFileSystem.getInstance().refreshAndFindFileByPath(jrtPath.toString())
        require(jrtDir != null) {
            "Could not find newly created JRT directory at `$jrtPath`."
        }

        val jrtRootUrl = VirtualFileManager.constructUrl(JrtFileSystem.PROTOCOL, jrtPath.toString() + JrtFileSystem.SEPARATOR)
        jrtRoot = VirtualFileManager.getInstance().findFileByUrl(jrtRootUrl)
            ?: error("Could not find JRT root with path `$jrtPath` using URL `$jrtRootUrl`.")

        require(JrtFileSystem.isRoot(jrtRoot)) {
            "The virtual file at `$jrtRootUrl` is not recognized as a JRT root."
        }
    }

    private fun releaseJrtFileSystem() {
        (jrtRoot.fileSystem as JrtFileSystemImpl).release(FileUtil.toSystemIndependentName(jrtPath.toString()))
    }
}
