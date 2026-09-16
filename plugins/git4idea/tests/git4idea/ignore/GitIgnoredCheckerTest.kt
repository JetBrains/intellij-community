// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.ignore

import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.Disposable
import com.intellij.openapi.vcs.Ignored
import com.intellij.openapi.vcs.NotIgnored
import com.intellij.openapi.vcs.VcsIgnoreChecker
import com.intellij.openapi.vcs.changes.VcsIgnoreManagerImpl
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VfsUtil
import git4idea.GitVcs
import git4idea.repo.GitRepositoryFiles
import git4idea.test.GitPlatformTestContext
import git4idea.test.createRepository
import git4idea.test.gitPlatformContextFixture
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.TestDisposable
import java.io.File
import java.io.IOException
import java.nio.file.Path

private const val folderName = "new_folder"

@TestApplication
internal class GitIgnoredCheckerTest {
  private val fixture = gitPlatformContextFixture()
  private val context: GitPlatformTestContext get() = fixture.get()

  @TestDisposable
  lateinit var testDisposable: Disposable

  private lateinit var gitIgnoreChecker: VcsIgnoreChecker
  private lateinit var gitIgnore: File

  @BeforeEach
  fun setUp(): Unit = with(context) {
    createRepository(project, projectPath)
    gitIgnoreChecker = VcsIgnoreManagerImpl.EP_NAME.extensionList.find { it.supportedVcs == GitVcs.getKey() }
                       ?: throw IllegalStateException("Cannot find registered GitRootChecker")
    gitIgnore = File("$projectPath/${GitRepositoryFiles.GITIGNORE}").apply {
      createNewFile()
      LocalFileSystem.getInstance().refreshIoFiles(setOf(this))
    }
  }

  @Test
  fun `test ignored in gitignore`(): Unit = with(context) {
    val dir = WriteAction.computeAndWait<Path, IOException> {
      VfsUtil.createDirectoryIfMissing(projectRoot, folderName).toNioPath()
    }

    gitIgnore.writeText("$folderName/")
    assertThat(gitIgnoreChecker.isIgnored(projectRoot, dir)).isInstanceOf(Ignored::class.java)

    gitIgnore.writeText(folderName)
    assertThat(gitIgnoreChecker.isIgnored(projectRoot, dir)).isInstanceOf(Ignored::class.java)

    gitIgnore.writeText("*")
    assertThat(gitIgnoreChecker.isIgnored(projectRoot, dir)).isInstanceOf(Ignored::class.java)
  }

  @Test
  fun `test not ignored in gitignore`(): Unit = with(context) {
    val dir = WriteAction.computeAndWait<Path, IOException> {
      VfsUtil.createDirectoryIfMissing(projectRoot, folderName).toNioPath()
    }

    assertThat(gitIgnoreChecker.isIgnored(projectRoot, dir)).isInstanceOf(NotIgnored::class.java)

    gitIgnore.writeText("!$folderName/")
    assertThat(gitIgnoreChecker.isIgnored(projectRoot, dir)).isInstanceOf(NotIgnored::class.java)

    gitIgnore.writeText("!$folderName")
    assertThat(gitIgnoreChecker.isIgnored(projectRoot, dir)).isInstanceOf(NotIgnored::class.java)

    gitIgnore.writeText("*\n!$folderName\n!$folderName/**")
    assertThat(gitIgnoreChecker.isIgnored(projectRoot, dir)).isInstanceOf(NotIgnored::class.java)
  }
}