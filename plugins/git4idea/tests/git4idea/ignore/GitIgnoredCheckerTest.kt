// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.ignore

import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.vcs.Ignored
import com.intellij.openapi.vcs.NotIgnored
import com.intellij.openapi.vcs.VcsIgnoreChecker
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VfsUtilCore
import git4idea.GitVcs
import git4idea.repo.GitRepositoryFiles
import git4idea.test.GitPlatformTest
import git4idea.test.createRepository
import java.io.File
import java.io.IOException

private const val folderName = "new_folder"

class GitIgnoredCheckerTest : GitPlatformTest() {

  private lateinit var gitIgnoreChecker: VcsIgnoreChecker
  private lateinit var gitIgnore: File

  override fun setUp() {
    super.setUp()
    createRepository(project, projectPath)
    gitIgnoreChecker = VcsIgnoreChecker.EXTENSION_POINT_NAME.getExtensionList(project).find { it.supportedVcs == GitVcs.getKey() }
      ?: throw IllegalStateException("Cannot find registered GitRootChecker")
    gitIgnore = File("$projectPath/${GitRepositoryFiles.GITIGNORE}").apply {
      createNewFile()
      LocalFileSystem.getInstance().refreshIoFiles(setOf(this))
    }
  }

  fun `test ignored in gitignore`() {
    val dir = WriteAction.computeAndWait<File, IOException> {
      VfsUtilCore.virtualToIoFile(VfsUtil.createDirectoryIfMissing(projectRoot, folderName))
    }

    gitIgnore.writeText("$folderName/")
    assertTrue(gitIgnoreChecker.isIgnored(projectRoot, dir) is Ignored)

    gitIgnore.writeText(folderName)
    assertTrue(gitIgnoreChecker.isIgnored(projectRoot, dir) is Ignored)

    gitIgnore.writeText("*")
    assertTrue(gitIgnoreChecker.isIgnored(projectRoot, dir) is Ignored)
  }

  fun `test not ignored in gitignore`() {
    val dir = WriteAction.computeAndWait<File, IOException> {
      VfsUtilCore.virtualToIoFile(VfsUtil.createDirectoryIfMissing(projectRoot, folderName))
    }

    assertTrue(gitIgnoreChecker.isIgnored(projectRoot, dir) is NotIgnored)

    gitIgnore.writeText("!$folderName/")
    assertTrue(gitIgnoreChecker.isIgnored(projectRoot, dir) is NotIgnored)

    gitIgnore.writeText("!$folderName")
    assertTrue(gitIgnoreChecker.isIgnored(projectRoot, dir) is NotIgnored)

    gitIgnore.writeText("*\n!$folderName\n!$folderName/**")
    assertTrue(gitIgnoreChecker.isIgnored(projectRoot, dir) is NotIgnored)
  }
}