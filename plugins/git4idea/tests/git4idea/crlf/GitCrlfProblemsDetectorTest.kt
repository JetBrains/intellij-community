// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.crlf

import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.junit5.TestApplication
import git4idea.test.GitSingleRepoContext
import git4idea.test.git
import git4idea.test.gitSingleRepoContextFixture
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import kotlin.io.path.writeText

@TestApplication
class GitCrlfProblemsDetectorTest {
  private val fixture = gitSingleRepoContextFixture()
  private val context: GitSingleRepoContext get() = fixture.get()

  @BeforeEach
  fun setUp(): Unit = with(context) {
    git("config core.autocrlf false")
  }

  @Test
  fun `test no warning if autocrlf is true`(): Unit = with(context) {
    git("config core.autocrlf true")
    assertThat(detect("temp").shouldWarn())
      .describedAs("No warning should be done if core.autocrlf is true")
      .isFalse()
  }

  @Test
  fun `test no warning if autocrlf is input`(): Unit = with(context) {
    git("config core.autocrlf input")
    assertThat(detect("temp").shouldWarn())
      .describedAs("No warning should be done if core.autocrlf is input")
      .isFalse()
  }

  @Test
  fun `test no warning if no files with CRLF`(): Unit = with(context) {
    createFile("temp", "Unix file\nNice separators\nOnly LF\n")
    assertThat(detect("temp").shouldWarn())
      .describedAs("No warning should be done if all files are LFs")
      .isFalse()
  }

  @Test
  fun `test no warning if file with CRLF but text is set`(): Unit = with(context) {
    gitattributes("*       text=auto")
    createCrlfFile("win")
    assertThat(detect("win").shouldWarn())
      .describedAs("No warning should be done if the file has a text attribute")
      .isFalse()
  }

  @Test
  fun `test no warning if file with CRLF but crlf is set`(): Unit = with(context) {
    gitattributes("win       crlf")
    createCrlfFile("win")
    assertThat(detect("win").shouldWarn())
      .describedAs("No warning should be done if the file has a crlf attribute")
      .isFalse()
  }

  @Test
  fun `test no warning if file with CRLF but crlf is explicitly unset`(): Unit = with(context) {
    gitattributes("win       -crlf")
    createCrlfFile("win")
    assertThat(detect("win").shouldWarn())
      .describedAs("No warning should be done if the file has an explicitly unset crlf attribute")
      .isFalse()
  }

  @Test
  fun `test no warning if file with CRLF but crlf is set to input`(): Unit = with(context) {
    gitattributes("wi*       crlf=input")
    createCrlfFile("win")
    assertThat(detect("win").shouldWarn())
      .describedAs("No warning should be done if the file has a crlf attribute")
      .isFalse()
  }

  @Test
  fun `test warning if file with CRLF no attrs autocrlf is false`(): Unit = with(context) {
    createCrlfFile("win")
    assertThat(detect("win").shouldWarn())
      .describedAs("Warning should be done if the file has CRLFs inside, and no explicit attributes")
      .isTrue()
  }

  @Test
  fun `test warning if various files with various attributes one does not match`(): Unit = with(context) {
    gitattributes("\nwin1 text crlf diff\nwin2 -text\nwin3 text=auto\nwin4 crlf\nwin5 -crlf\nwin6 crlf=input\n")

    createFile("unix", "Unix file\nNice separators\nOnly LF\n")
    createCrlfFile("win1")
    createCrlfFile("win2")
    createCrlfFile("win3")
    createCrlfFile("src/win4")
    createCrlfFile("src/win5")
    createCrlfFile("src/win6")
    createCrlfFile("src/win7")

    val files = listOf("unix", "win1", "win2", "win3", "src/win4", "src/win5", "src/win6", "src/win7")
      .map { findFile(projectNioRoot.resolve(it)) }
    assertThat(GitCrlfProblemsDetector.detect(project, git, files).shouldWarn())
      .describedAs("Warning should be done, since one of the files has CRLFs and no related attributes")
      .isTrue()
  }

  private fun GitSingleRepoContext.gitattributes(content: String) {
    createFile(".gitattributes", content)
  }

  private fun GitSingleRepoContext.detect(relativePath: String): GitCrlfProblemsDetector = detect(findFile(createFile(relativePath)))

  private fun GitSingleRepoContext.detect(file: VirtualFile): GitCrlfProblemsDetector =
    GitCrlfProblemsDetector.detect(project, git, listOf(file))

  private fun GitSingleRepoContext.createCrlfFile(relativePath: String) {
    createFile(relativePath, "Windows file\r\nBad separators\r\nCRLF in action\r\n")
  }

  private fun GitSingleRepoContext.createFile(relativePath: String, content: String = ""): Path {
    val file = projectNioRoot.resolve(relativePath)
    Files.createDirectories(file.parent)
    if (Files.notExists(file)) Files.createFile(file)
    file.writeText(content, Charsets.UTF_8, StandardOpenOption.APPEND)
    return file
  }

  private fun findFile(file: Path): VirtualFile =
    checkNotNull(VfsUtil.findFile(file, true))
}
