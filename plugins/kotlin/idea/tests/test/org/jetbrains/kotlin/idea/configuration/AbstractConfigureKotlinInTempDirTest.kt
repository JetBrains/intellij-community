// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.configuration

import com.intellij.openapi.Disposable
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.util.Ref
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.refreshAndFindVirtualDirectory
import com.intellij.util.ThrowableRunnable
import com.intellij.util.indexing.FileBasedIndex
import org.jetbrains.kotlin.idea.base.plugin.artifacts.TestKotlinArtifacts
import org.jetbrains.kotlin.idea.test.IDEA_TEST_DATA_DIR
import org.jetbrains.kotlin.idea.test.KotlinTestUtils
import org.jetbrains.kotlin.idea.test.runAll
import java.io.File
import java.nio.file.Files
import java.nio.file.Path

abstract class AbstractConfigureKotlinInTempDirTest : AbstractConfigureKotlinTest() {
    companion object {
        private val logger = logger<AbstractConfigureKotlinInTempDirTest>()
    }
    private lateinit var vfsDisposable: Ref<Disposable>

    override fun createProjectRoot(): File = KotlinTestUtils.tmpDirForReusableFolder("configure_$projectName")

    override fun setUp() {
        super.setUp()
        vfsDisposable = KotlinTestUtils.allowRootAccess(this, projectRoot.path)
    }

    override fun tearDown(): Unit = runAll(
        ThrowableRunnable { KotlinTestUtils.disposeVfsRootAccess(vfsDisposable) },
        ThrowableRunnable { super.tearDown() },
    )

    override fun getProjectDirOrFile(isDirectoryBasedProject: Boolean): Path {
        logger.debug("Copying files to temp directory")
        val originalDir = IDEA_TEST_DATA_DIR.resolve("configuration").resolve(projectName)
        if (!originalDir.copyRecursively(projectRoot)) {
            logger.warn("Failed to copy files to temp directory")
        }
        val projectFile = projectRoot.resolve("projectFile.ipr")
        val projectRoot = (if (projectFile.exists()) projectFile else projectRoot).toPath()

        val testName = getTestName(true).toLowerCase()
        val originalStdlibFile = if (testName.contains("latestruntime") || testName.endsWith("withstdlib"))
            TestKotlinArtifacts.kotlinStdlib
        else
            null

        if (originalStdlibFile != null) {
            val stdlibPath = "lib/kotlin-stdlib.jar"
            val originalPath = originalDir.resolve(stdlibPath)
            if (Files.exists(originalPath.toPath())) error(originalPath)
            val kotlinStdlib = projectRoot.resolve(stdlibPath)
            originalStdlibFile.copyTo(kotlinStdlib.toFile(), overwrite = true)
        }
        // Needed, so the index knows that there are Kotlin files in the project
        VfsUtil.markDirtyAndRefresh(false, true, true, this.projectRoot.toPath().refreshAndFindVirtualDirectory())
        FileBasedIndex.getInstance().invalidateCaches()
        logger.debug("Files copied successfully and file cache invalidated")

        return projectRoot
    }
}
