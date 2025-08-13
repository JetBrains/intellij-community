// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.commit

import com.intellij.mock.MockVirtualFile
import com.intellij.openapi.vfs.VirtualFile
import org.junit.Test
import org.junit.experimental.runners.Enclosed
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import org.junit.runners.Parameterized.Parameter
import java.nio.file.Paths
import kotlin.io.path.pathString

@RunWith(Enclosed::class)
class IntelliJPlatformPrePushHandlerTest {

  @RunWith(Parameterized::class)
  class FilesBelongToPlatform {
    companion object {
      @JvmStatic
      @Parameterized.Parameters(name = "{0}")
      fun provideTestParameters(): Iterable<List<String>> {
        return listOf(
          listOf("${COMMUNITY_PLATFORM}/File.kt"),
          listOf(
            "${COMMUNITY_PLATFORM}/File.kt",
            "root.txt",
          ),
          listOf(
            "${COMMUNITY_PLATFORM}/File.kt",
            "${COMMUNITY}/plugins/stuff.xml",
            "not-important-dir-A/whatever",
            "not-important-dir-B/whatever"
          ),
          listOf("unrelated/$COMMUNITY_PLATFORM/File.kt"),
        )
      }
    }

    @Parameter
    lateinit var files: List<String>

    @Test
    fun testThatChecksForFileSet() {
      val filesSet = files.map { fileAt(it) }
      assert(prePushHandler.containSources(filesSet)) {
        "The following set of files doesn't trigger the check: $filesSet"
      }
    }
  }


  @RunWith(Parameterized::class)
  class FilesDoNotBelongToPlatform {
    companion object {
      @JvmStatic
      @Parameterized.Parameters(name = "{0}")
      fun provideTestParameters(): Iterable<List<String>> {
        return listOf(
          listOf("${COMMUNITY_PLATFORM}/README.md"),
          listOf("${COMMUNITY_PLATFORM}/sub/sub.iml"),

          listOf("${COMMUNITY}/File.kt"),
          listOf("${COMMUNITY}/README.md"),
          listOf("${COMMUNITY}/sub/sub.iml"),
          listOf(
            "${COMMUNITY}/plugins/stuff.xml",
            "not-important-dir-A/whatever",
            "not-important-dir-B/whatever",
          ),
        )
      }
    }

    @Parameter
    lateinit var files: List<String>

    @Test
    fun testThatIgnoresFileSet() {
      val filesSet = files.map { fileAt(it) }
      assert(!prePushHandler.containSources(filesSet)) {
        "The following set of files triggered the check: $filesSet"
      }
    }
  }

  @RunWith(Parameterized::class)
  class ValidCommitMessages {

    companion object {
      @JvmStatic
      @Parameterized.Parameters(name = "{0}")
      fun provideTestParameters(): Iterable<String> {
        return listOf(
          "IJPL-1", "A-1",
          "IJPL-123", "(IJPL-123)", "[IJPL-123]", "{IJPL-123}", "'IJPL-123'", "`IJPL-123`", "\"IJPL-123\"",
          "IDEA-123", "(IDEA-123)", "[IDEA-123]", "{IDEA-123}", "'IDEA-123'", "`IDEA-123`", "\"IDEA-123\"",
          "IJPL-42",
          "KTIJ-123  ", "  KTIJ-123", "  KTIJ-123  ", "IDEA-123  ",
          "  IDEA-123", "  IDEA-123  ",
          "IJPL-123 header",
          "Header IJPL-123",
          "Header IJPL-123 header",
          "IJPL-123 header",
          "Header IJPL-123",
          "Header IJPL-123 header",
          """
            Header IJPL-123 header
            
            Body-line-1
            Body-line-N
          """.trimIndent(),
          """
            Header IJPL-123 header
            
            Body-line-1
            Body-line-N
          """.trimIndent(),
          """
            Header IJPL-123 header
            
            Body-line-1
            Body-line-N

            ^IJPL-123 fixed
          """.trimIndent(),
          """
            Header IJPL-123 header
            
            Body-line-1
            Body-line-N

            ^IDEA-123 fixed
          """.trimIndent(),
          """
            Header IJPL-42 header
            
            Body-line-1
            Body-line-N

            ^IDEA-123 fixed
          """.trimIndent(),
          """
            Header
            
            Body-line-1
            Body-line-N

            ^IJPL-123 fixed
          """.trimIndent(),
          """
            Header
            
            Body-line-1
            Body-line-N

            ^IDEA-123 fixed
          """.trimIndent(),
          """
            Header
            
            Body-line-1
            Body-line-N

            #KTIJ-123 fixed
          """.trimIndent(),
          """
            Header
            
            Body-line-1
            Body-line-N

            #IDEA-123 fixed
          """.trimIndent(),
          """
            Header
            
            Body-line-1
            Body-line-N

            Relates to #KTIJ-123
          """.trimIndent(),
          """
            Header
            
            Body-line-1
            Body-line-N

            Relates to #IDEA-123
          """.trimIndent(),
          """
            Header
            
            Body-line-1
            Body-line-N
            
            ^WHATEVER-123 fixed
          """.trimIndent(),
          """
            Header
            
            Body-line-1
            Body-line-N
            
            #WHATEVER-123 fixed
          """.trimIndent(),
          """
            Header
            
            Body-line-1
            Body-line-N
            
            Relates to #WHATEVER-123
          """.trimIndent(),

          "IJ-MR-123", "IJ-CR-0",

          "test thing", "test: thing", "[test] thing", "Test thing",
          "tests thing", "tests: thing", "[tests] thing",
          "cleanup thing", "cleanup: thing", "[cleanup] thing", "Cleanup stuff",
          "docs thing", "docs: thing", "[docs] thing", "Docs thing",
          "doc thing", "doc: thing", "[doc] thing", "Doc very much",
          "typo thing", "typo: thing", "[typo] thing",
          "format thing", "format: thing", "[format] thing",
          "style thing", "style: thing", "[style] thing",
          "refactor this thing", "refactor: this thing",

          "Cleanup (reason)",
          "[subsystem][tests] new tests",
          "[subsystem] docs: fix typo",
          "[smth][refactoring] do stuff",

          "[testFramework] whatever",
          "[test framework] whatever",
          "test framework",
        )
      }
    }

    @Parameter
    lateinit var commitMessage: String

    @Test
    fun testThatCommitMessageIsValid() {
      assert(prePushHandler.commitMessageIsCorrect(commitMessage)) {
        "The following commit message was considered invalid: $commitMessage"
      }
    }
  }

  @RunWith(Parameterized::class)
  class InvalidCommitMessages {

    companion object {
      @JvmStatic
      @Parameterized.Parameters(name = "{0}")
      fun provideTestParameters(): Iterable<String> {
        return listOf(
          "",
          "IJPL",
          "IJPL-",
          "IJPL-one",
          "IJPL 1",
          "IJPL111",
          "Text IJPL Text",
          "something-something",
          "2-fold",
          "AB-RA-CA-DA-BRA-1",
          """"
            Header
            
            Body-line-1
            Body-line-N
          """.trimIndent(),

          "test", "test:", "[test]", "test ", "add test", "drop test",
          "tests", "tests:", "[tests]", "tests ", "add tests", "drop tests",
          "cleanup", "cleanup:", "[cleanup]", "cleanup ", "do cleanup",
          "docs", "docs:", "[docs]", "docs ", "add docs",
          "doc", "doc:", "[doc]", "doc ", "add doc",
          "typo", "typo:", "[typo]", "typo ", "fix typo",
          "format", "format:", "[format]", "fix format", "fix formatting",
          "style", "style:", "[style]", "fix style",

          "stuff", "stuff:", "[stuff]", "very important stuff",
          "refactor", "refactor:", "[refactor]",
          "refactoring", "refactoring:", "[refactoring]",

          "[subsystem] Do very important stuff very very important",
          "platform: add 'thing' here and there",
          "subsystem tests: use junit5 instead of junit4",
          "subsystem: cleanup",
          "cool: 5 new tests",
          "Fix test",
          "[subsystem] docs",
          "null",
        )
      }
    }

    @Parameter
    lateinit var commitMessage: String

    @Test
    fun testThatCommitMessageIsValid() {
      assert(!prePushHandler.commitMessageIsCorrect(commitMessage)) {
        "The following commit message was considered as valid: $commitMessage"
      }
    }
  }
}

private val prePushHandler = IntelliJPlatformPrePushHandler()
private val tempDir: String = System.getProperty("java.io.tmpdir")
private const val COMMUNITY_PLATFORM = "community/platform/"
private const val COMMUNITY = "community/"

private fun fileAt(path: String): VirtualFile {
  val path: String =  Paths.get(tempDir, *path.split("/").filterNot { it.isEmpty() }.toTypedArray()).pathString
  return MockVirtualFile(path)
}