// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.k2.highlighting

import com.intellij.diff.DiffContentFactory
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.PsiTestUtil
import com.intellij.testFramework.VfsTestUtil
import com.intellij.testFramework.fixtures.TempDirTestFixture
import com.intellij.testFramework.fixtures.impl.TempDirTestFixtureImpl
import com.intellij.testFramework.utils.editor.getVirtualFile
import com.intellij.util.ThrowableRunnable
import org.jetbrains.kotlin.idea.base.plugin.KotlinPluginKind
import org.jetbrains.kotlin.idea.base.test.KotlinJvmLightProjectDescriptor
import org.jetbrains.kotlin.idea.base.test.NewLightKotlinCodeInsightFixtureTestCase
import org.jetbrains.kotlin.idea.base.util.KOTLIN_FILE_EXTENSIONS
import org.jetbrains.kotlin.idea.test.runAll
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.io.path.*

abstract class AbstractOutsiderHighlightingTest : NewLightKotlinCodeInsightFixtureTestCase() {
    override val pluginKind: KotlinPluginKind
        get() = KotlinPluginKind.K2

    override fun getProjectDescriptor() = KotlinJvmLightProjectDescriptor.DEFAULT

    override fun getTempDirFixture(): TempDirTestFixture {
        return TempDirTestFixtureImpl()
    }

    private val sourceRootVirtualFile: VirtualFile
        get() = LocalFileSystem.getInstance().findFileByPath(myFixture.tempDirFixture.tempDirPath)!!

    override fun setUp() {
        super.setUp()

        // Outsider files don't get along with files placed outside the LocalFileSystem. 'OutsidersPsiFileSupport.FILE_PATH_KEY'
        // is supposed to act as a bond between an outsider file and some physical counterpart and only stores the original file path.
        // Light code-insight tests use the 'temp://' file system, so 'OutsidersPsiFileSupport.getOriginalFilePath()' returns 'null',
        // and Kotlin refuses to highlight outsider files as there is no origin (KotlinHighlightingUtils.kt, 'shouldHighlightFile()').
        PsiTestUtil.addSourceRoot(module, sourceRootVirtualFile)
    }

    override fun tearDown() {
        runAll(
            ThrowableRunnable { PsiTestUtil.removeSourceRoot(module, sourceRootVirtualFile) },
            ThrowableRunnable { super.tearDown() }
        )
    }

    protected fun performTest() {
        val testPath = testRootPath.resolve(testMethodPath)

        val localFileSystem = LocalFileSystem.getInstance()

        val srcDirPath = testPath.resolve("src")
        myFixture.copyDirectoryToProject(srcDirPath.relativeTo(testRootPath).toString(), "")

        val srcTempDirPath = Paths.get(myFixture.tempDirFixture.tempDirPath)
        val diffDirPath = testPath.resolve("diff")

        for (srcPath in srcTempDirPath.listKotlinFiles()) {
            val srcVirtualFile = localFileSystem.findFileByNioFile(srcPath)!!
            srcVirtualFile.bindTestPath(srcPath)

            myFixture.openFileInEditor(srcVirtualFile)
            myFixture.checkHighlighting()
        }

        val diffContentFactory = DiffContentFactory.getInstance()

        for (diffPath in diffDirPath.listKotlinFiles()) {
            val correspondingSrcPath = srcTempDirPath.resolve(diffPath.relativeTo(diffDirPath))
            val correspondingSrcVirtualFile = localFileSystem.findFileByNioFile(correspondingSrcPath)!!

            val diffDocument = diffContentFactory.create(project, diffPath.readText(), correspondingSrcVirtualFile).document
            diffDocument.setReadOnly(false) // For 'ExpectedHighlightingData'

            val diffVirtualFile = diffDocument.getVirtualFile()
            diffVirtualFile.bindTestPath(diffPath)

            myFixture.openFileInEditor(diffVirtualFile)

            // Note: errors from outsider files are explicitly disabled. Use warnings instead.
            // See OutsidersPsiFileSupport.HighlightFilter.
            myFixture.checkHighlighting()
        }
    }

    private fun Path.listKotlinFiles(): List<Path> {
        return listDirectoryEntries("*.{" + KOTLIN_FILE_EXTENSIONS.joinToString(",") + "}")
    }

    private fun VirtualFile.bindTestPath(path: Path) {
        putUserData(VfsTestUtil.TEST_DATA_FILE_PATH, path.toAbsolutePath().toString())
    }
}
