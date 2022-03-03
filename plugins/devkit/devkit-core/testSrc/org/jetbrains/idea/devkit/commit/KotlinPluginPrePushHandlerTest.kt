// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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
class KotlinPluginPrePushHandlerTest {

  @RunWith(Parameterized::class)
  class FilesBelongToPlugin {
    companion object {
      @JvmStatic
      @Parameterized.Parameters(name = "{0}")
      fun provideTestParameters(): Iterable<List<Pair<String, String>>> {
        return listOf(
          listOf(ULTIMATE_KOTLIN_PLUGIN to "File.kt"),
          listOf(COMMUNITY_KOTLIN_PLUGIN to "File.kt"),

          listOf(ULTIMATE_KOTLIN_PLUGIN to "File.kts"),
          listOf(COMMUNITY_KOTLIN_PLUGIN to "File.kts"),

          listOf(ULTIMATE_KOTLIN_PLUGIN to "File.java"),
          listOf(COMMUNITY_KOTLIN_PLUGIN to "File.java"),

          listOf(ULTIMATE_KOTLIN_PLUGIN to "File.properties"),
          listOf(COMMUNITY_KOTLIN_PLUGIN to "File.properties"),

          listOf(ULTIMATE_KOTLIN_PLUGIN to "File.html"),
          listOf(COMMUNITY_KOTLIN_PLUGIN to "File.html"),

          listOf(ULTIMATE_KOTLIN_PLUGIN to "File.xml"),
          listOf(COMMUNITY_KOTLIN_PLUGIN to "File.xml"),

          listOf(
            COMMUNITY_KOTLIN_PLUGIN to "File.kt",
            "not-important-dir-A" to "whatever-file",
            "not-important-dir-B" to "whatever-file"
          ),

          listOf(
            "not-important-dir-A" to "whatever-file",
            COMMUNITY_KOTLIN_PLUGIN to "File.kt",
            "not-important-dir-B" to "whatever-file"
          ),

          listOf(
            "not-important-dir-A" to "whatever-file",
            "not-important-dir-B" to "whatever-file",
            COMMUNITY_KOTLIN_PLUGIN to "File.kt"
          )
        )
      }
    }

    @Parameter
    lateinit var files: List<Pair<String, String>>

    @Test
    fun testThatChecksForFileSet() {
      val filesSet = files.map { fileAt(it.first, it.second) }
      assert(KotlinPluginPrePushHandler.containKotlinPluginSources(filesSet)) {
        "The following set of files doesn't trigger the check: $filesSet"
      }
    }
  }


  @RunWith(Parameterized::class)
  class FilesDoNotBelongToPlugin {
    companion object {
      @JvmStatic
      @Parameterized.Parameters(name = "{0}")
      fun provideTestParameters(): Iterable<List<Pair<String, String>>> {
        return listOf(
          // file path has no kotlin-plugin subdirectory
          listOf("not-important-dir" to "File.kt"),
          listOf(FLEET_KOTLIN_PLUGIN to "File.kt"),

          // kotlin-plugin path contains subdirectories to exclude
          listOf("$ULTIMATE_KOTLIN_PLUGIN/test/" to "File.kt"),
          listOf("$ULTIMATE_KOTLIN_PLUGIN/testData/" to "File.kt"),

          // file extensions to exclude
          listOf(COMMUNITY_KOTLIN_PLUGIN to "File.iml"),
          listOf(COMMUNITY_KOTLIN_PLUGIN to "File.md"),

          // multiple files, none matches
          listOf(
            "not-important-dir" to "File.kt",
            FLEET_KOTLIN_PLUGIN to "File.kt",
            "$ULTIMATE_KOTLIN_PLUGIN/test/" to "File.kt",
            "$ULTIMATE_KOTLIN_PLUGIN/testData/" to "File.kt",
            COMMUNITY_KOTLIN_PLUGIN to "File.iml",
            COMMUNITY_KOTLIN_PLUGIN to "File.md"
          )
        )
      }
    }

    @Parameter
    lateinit var files: List<Pair<String, String>>

    @Test
    fun testThatIgnoresFileSet() {
      val filesSet = files.map { fileAt(it.first, it.second) }
      assert(!KotlinPluginPrePushHandler.containKotlinPluginSources(filesSet)) {
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
          "KTIJ-123",
          "(KTIJ-123)",
          "[KTIJ-123]",
          "{KTIJ-123}",
          "'KTIJ-123'",
          "`KTIJ-123`",
          "\"KTIJ-123\"",

          "KTIJ-123  ",
          "  KTIJ-123",
          "  KTIJ-123  ",

          "KTIJ-123 header",
          "Header KTIJ-123",
          "Header KTIJ-123 header",

          """
            Header KTIJ-123 header
            
            Body-line-1
            Body-line-N
          """.trimIndent(),

          """
            Header KTIJ-123 header
            
            Body-line-1
            Body-line-N

            ^KTIJ-123 fixed
          """.trimIndent(),

          """
            Header
            
            Body-line-1
            Body-line-N

            ^KTIJ-123 fixed
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

            Relates to #KTIJ-123
          """.trimIndent()
        )
      }
    }

    @Parameter
    lateinit var commitMessage: String

    @Test
    fun testThatCommitMessageIsValid() {
      assert(KotlinPluginPrePushHandler.commitMessageIsCorrect(commitMessage)) {
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
          "KTIJ",
          "KTIJ-",
          "KTIJ-one",
          "KT-123",
          "IDEA-123",
          "WHATEVER-123",
          "Header KTIJ",
          "Header KTIJ header",

          """
            Header
            
            Body-line-1
            Body-line-N
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
            
            #IDEA-123 fixed
          """.trimIndent(),

          """
            Header
            
            Body-line-1
            Body-line-N
            
            Relates to #IDEA-123
          """.trimIndent(),
        )
      }
    }

    @Parameter
    lateinit var commitMessage: String

    @Test
    fun testThatCommitMessageIsValid() {
      assert(!KotlinPluginPrePushHandler.commitMessageIsCorrect(commitMessage)) {
        "The following commit message was considered as valid: $commitMessage"
      }
    }
  }
}


private val tempDir: String = System.getProperty("java.io.tmpdir")
private const val ULTIMATE_KOTLIN_PLUGIN = "plugins/kotlin/package/"
private const val COMMUNITY_KOTLIN_PLUGIN = "community/plugins/kotlin/package/"
private const val FLEET_KOTLIN_PLUGIN = "fleet/plugins/kotlin/package/"


private fun fileAt(dirPath: String, fileName: String): VirtualFile {
  val path: String =  Paths.get(tempDir, *dirPath.split("/").toTypedArray(), fileName).pathString
  return MockVirtualFile(path)
}