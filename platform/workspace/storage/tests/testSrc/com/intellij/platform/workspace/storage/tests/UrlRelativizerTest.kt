// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.platform.workspace.storage.tests

import com.intellij.platform.workspace.storage.impl.url.BasePath
import com.intellij.platform.workspace.storage.impl.url.UrlRelativizerImpl
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import java.util.stream.Stream
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class UrlRelativizerTest {

  private lateinit var urlRelativizer: UrlRelativizerImpl

  @BeforeEach
  fun setUp() {
    urlRelativizer = UrlRelativizerImpl(listOf(
      Pair("USER_HOME", "/Users/user123/"),
      Pair("FOLDER_INSIDE_PROJECT", "/Users/user123/project/folder"),
      Pair("PROJECT_DIR", "/Users/user123/project"),
      Pair("EXTERNAL_DIR", "/Users/.external/")
    ))
  }

  @ParameterizedTest
  @MethodSource("base paths for checking conversion")
  fun `check conversion between absolute and relative paths`(absoluteUrl1: String, relativeUrl: String, absoluteUrl2: String) {
    // First, check conversion with one path relativizer
    // Check conversion absoluteUrl1 <-> relativeUrl
    assertEquals(relativeUrl, urlRelativizer.toRelativeUrl(absoluteUrl1))
    assertEquals(absoluteUrl1, urlRelativizer.toAbsoluteUrl(relativeUrl))

    // Next, check conversion between two path relativizers
    val otherPathRelativizer = UrlRelativizerImpl(listOf(
      Pair("USER_HOME", "C:\\Users\\otherUser"),
      Pair("FOLDER_INSIDE_PROJECT", "C:\\Project\\folder"),
      Pair("PROJECT_DIR", "C:\\Project"),
      Pair("EXTERNAL_DIR", "C:\\Users\\otherUser\\Downloads")
    ))

    // Check conversion from absoluteUrl1 -> relativeUrl1 -> absoluteUrl2
    val relativeUrl1 = urlRelativizer.toRelativeUrl(absoluteUrl1)
    assertEquals(absoluteUrl2, otherPathRelativizer.toAbsoluteUrl(relativeUrl1))

    // Check conversion from absoluteUrl2 -> relativeUrl2 -> absoluteUrl1
    val relativeUrl2 = otherPathRelativizer.toRelativeUrl(absoluteUrl2)
    assertEquals(absoluteUrl1, urlRelativizer.toAbsoluteUrl(relativeUrl2))
  }

  @Test
  fun `when converted relative path should be system independent`() {
    val absoluteUrl = "\\Users\\user123\\project\\x\\y\\z.txt"
    val expectedRelativeUrl = "\$PROJECT_DIR$/x/y/z.txt"
    assertEquals(expectedRelativeUrl, urlRelativizer.toRelativeUrl(absoluteUrl))
  }

  @Test
  fun `base paths should be system independent`() {
    urlRelativizer.addBasePathWithProtocols("NEW_PATH", "\\Users\\folder\\")

    assertContains(urlRelativizer.basePaths, BasePath("\$NEW_PATH$", "/Users/folder"))
    assertFalse {
      urlRelativizer.basePaths.contains(BasePath("\$NEW_PATH$", "\\Users\\folder"))
    }
    assertFalse {
      urlRelativizer.basePaths.contains(BasePath("\$NEW_PATH$", "\\Users\\folder\\"))
    }
  }

  @Test
  fun `base paths ending should be trimmed`() {
    urlRelativizer.addBasePathWithProtocols("NEW_PATH", "/Users/folder/")

    // base path url should not have '/' at the end
    assertContains(urlRelativizer.basePaths, BasePath("\$NEW_PATH$", "/Users/folder"))
    assertFalse {
      urlRelativizer.basePaths.contains(BasePath("\$NEW_PATH$", "/Users/folder/"))
    }
  }

  @Test
  fun `example of sorted base paths`() {
    val otherPathRelativizer = UrlRelativizerImpl(listOf(Pair("BASE", "/path")))
    val expectedBasePathList = listOf(
      BasePath("\$BASE$", "/path"),

      BasePath("file://\$BASE$", "file:///path"),
      BasePath("file:/\$BASE$", "file://path"),
      BasePath("file:\$BASE$", "file:/path"),

      BasePath("jar://\$BASE$", "jar:///path"),
      BasePath("jar:/\$BASE$", "jar://path"),
      BasePath("jar:\$BASE$", "jar:/path"),

      BasePath("jrt://\$BASE$", "jrt:///path"),
      BasePath("jrt:/\$BASE$", "jrt://path"),
      BasePath("jrt:\$BASE$", "jrt:/path"),
    )

    assertEquals(expectedBasePathList, otherPathRelativizer.basePaths)
  }

  @Test
  fun `base paths should be created with protocols and be sorted by length decreasingly`() {
    val expectedBasePathsList = listOf(
      BasePath("\$FOLDER_INSIDE_PROJECT$", "/Users/user123/project/folder"),
      BasePath("file://\$FOLDER_INSIDE_PROJECT$", "file:///Users/user123/project/folder"),
      BasePath("file:/\$FOLDER_INSIDE_PROJECT$", "file://Users/user123/project/folder"),
      BasePath("file:\$FOLDER_INSIDE_PROJECT$", "file:/Users/user123/project/folder"),
      BasePath("jar://\$FOLDER_INSIDE_PROJECT$", "jar:///Users/user123/project/folder"),
      BasePath("jar:/\$FOLDER_INSIDE_PROJECT$", "jar://Users/user123/project/folder"),
      BasePath("jar:\$FOLDER_INSIDE_PROJECT$", "jar:/Users/user123/project/folder"),
      BasePath("jrt://\$FOLDER_INSIDE_PROJECT$", "jrt:///Users/user123/project/folder"),
      BasePath("jrt:/\$FOLDER_INSIDE_PROJECT$", "jrt://Users/user123/project/folder"),
      BasePath("jrt:\$FOLDER_INSIDE_PROJECT$", "jrt:/Users/user123/project/folder"),

      BasePath("\$PROJECT_DIR$", "/Users/user123/project"),
      BasePath("file://\$PROJECT_DIR$", "file:///Users/user123/project"),
      BasePath("file:/\$PROJECT_DIR$", "file://Users/user123/project"),
      BasePath("file:\$PROJECT_DIR$", "file:/Users/user123/project"),
      BasePath("jar://\$PROJECT_DIR$", "jar:///Users/user123/project"),
      BasePath("jar:/\$PROJECT_DIR$", "jar://Users/user123/project"),
      BasePath("jar:\$PROJECT_DIR$", "jar:/Users/user123/project"),
      BasePath("jrt://\$PROJECT_DIR$", "jrt:///Users/user123/project"),
      BasePath("jrt:/\$PROJECT_DIR$", "jrt://Users/user123/project"),
      BasePath("jrt:\$PROJECT_DIR$", "jrt:/Users/user123/project"),

      BasePath("\$EXTERNAL_DIR$", "/Users/.external"),
      BasePath("file://\$EXTERNAL_DIR$", "file:///Users/.external"),
      BasePath("file:/\$EXTERNAL_DIR$", "file://Users/.external"),
      BasePath("file:\$EXTERNAL_DIR$", "file:/Users/.external"),
      BasePath("jar://\$EXTERNAL_DIR$", "jar:///Users/.external"),
      BasePath("jar:/\$EXTERNAL_DIR$", "jar://Users/.external"),
      BasePath("jar:\$EXTERNAL_DIR$", "jar:/Users/.external"),
      BasePath("jrt://\$EXTERNAL_DIR$", "jrt:///Users/.external"),
      BasePath("jrt:/\$EXTERNAL_DIR$", "jrt://Users/.external"),
      BasePath("jrt:\$EXTERNAL_DIR$", "jrt:/Users/.external"),

      BasePath("\$USER_HOME$", "/Users/user123"),
      BasePath("file://\$USER_HOME$", "file:///Users/user123"),
      BasePath("file:/\$USER_HOME$", "file://Users/user123"),
      BasePath("file:\$USER_HOME$", "file:/Users/user123"),
      BasePath("jar://\$USER_HOME$", "jar:///Users/user123"),
      BasePath("jar:/\$USER_HOME$", "jar://Users/user123"),
      BasePath("jar:\$USER_HOME$", "jar:/Users/user123"),
      BasePath("jrt://\$USER_HOME$", "jrt:///Users/user123"),
      BasePath("jrt:/\$USER_HOME$", "jrt://Users/user123"),
      BasePath("jrt:\$USER_HOME$", "jrt:/Users/user123"),
      )
    assertEquals(expectedBasePathsList, urlRelativizer.basePaths)
  }

  @Test
  fun `base paths should remain sorted after adding new base path`() {
    urlRelativizer.addBasePathWithProtocols("NEW_PATH", "/Users/user123/new-path")

    val expectedBasePathsList = listOf(
      BasePath("\$FOLDER_INSIDE_PROJECT$", "/Users/user123/project/folder"),
      BasePath("file://\$FOLDER_INSIDE_PROJECT$", "file:///Users/user123/project/folder"),
      BasePath("file:/\$FOLDER_INSIDE_PROJECT$", "file://Users/user123/project/folder"),
      BasePath("file:\$FOLDER_INSIDE_PROJECT$", "file:/Users/user123/project/folder"),
      BasePath("jar://\$FOLDER_INSIDE_PROJECT$", "jar:///Users/user123/project/folder"),
      BasePath("jar:/\$FOLDER_INSIDE_PROJECT$", "jar://Users/user123/project/folder"),
      BasePath("jar:\$FOLDER_INSIDE_PROJECT$", "jar:/Users/user123/project/folder"),
      BasePath("jrt://\$FOLDER_INSIDE_PROJECT$", "jrt:///Users/user123/project/folder"),
      BasePath("jrt:/\$FOLDER_INSIDE_PROJECT$", "jrt://Users/user123/project/folder"),
      BasePath("jrt:\$FOLDER_INSIDE_PROJECT$", "jrt:/Users/user123/project/folder"),

      BasePath("\$NEW_PATH$", "/Users/user123/new-path"),
      BasePath("file://\$NEW_PATH$", "file:///Users/user123/new-path"),
      BasePath("file:/\$NEW_PATH$", "file://Users/user123/new-path"),
      BasePath("file:\$NEW_PATH$", "file:/Users/user123/new-path"),
      BasePath("jar://\$NEW_PATH$", "jar:///Users/user123/new-path"),
      BasePath("jar:/\$NEW_PATH$", "jar://Users/user123/new-path"),
      BasePath("jar:\$NEW_PATH$", "jar:/Users/user123/new-path"),
      BasePath("jrt://\$NEW_PATH$", "jrt:///Users/user123/new-path"),
      BasePath("jrt:/\$NEW_PATH$", "jrt://Users/user123/new-path"),
      BasePath("jrt:\$NEW_PATH$", "jrt:/Users/user123/new-path"),

      BasePath("\$PROJECT_DIR$", "/Users/user123/project"),
      BasePath("file://\$PROJECT_DIR$", "file:///Users/user123/project"),
      BasePath("file:/\$PROJECT_DIR$", "file://Users/user123/project"),
      BasePath("file:\$PROJECT_DIR$", "file:/Users/user123/project"),
      BasePath("jar://\$PROJECT_DIR$", "jar:///Users/user123/project"),
      BasePath("jar:/\$PROJECT_DIR$", "jar://Users/user123/project"),
      BasePath("jar:\$PROJECT_DIR$", "jar:/Users/user123/project"),
      BasePath("jrt://\$PROJECT_DIR$", "jrt:///Users/user123/project"),
      BasePath("jrt:/\$PROJECT_DIR$", "jrt://Users/user123/project"),
      BasePath("jrt:\$PROJECT_DIR$", "jrt:/Users/user123/project"),

      BasePath("\$EXTERNAL_DIR$", "/Users/.external"),
      BasePath("file://\$EXTERNAL_DIR$", "file:///Users/.external"),
      BasePath("file:/\$EXTERNAL_DIR$", "file://Users/.external"),
      BasePath("file:\$EXTERNAL_DIR$", "file:/Users/.external"),
      BasePath("jar://\$EXTERNAL_DIR$", "jar:///Users/.external"),
      BasePath("jar:/\$EXTERNAL_DIR$", "jar://Users/.external"),
      BasePath("jar:\$EXTERNAL_DIR$", "jar:/Users/.external"),
      BasePath("jrt://\$EXTERNAL_DIR$", "jrt:///Users/.external"),
      BasePath("jrt:/\$EXTERNAL_DIR$", "jrt://Users/.external"),
      BasePath("jrt:\$EXTERNAL_DIR$", "jrt:/Users/.external"),

      BasePath("\$USER_HOME$", "/Users/user123"),
      BasePath("file://\$USER_HOME$", "file:///Users/user123"),
      BasePath("file:/\$USER_HOME$", "file://Users/user123"),
      BasePath("file:\$USER_HOME$", "file:/Users/user123"),
      BasePath("jar://\$USER_HOME$", "jar:///Users/user123"),
      BasePath("jar:/\$USER_HOME$", "jar://Users/user123"),
      BasePath("jar:\$USER_HOME$", "jar:/Users/user123"),
      BasePath("jrt://\$USER_HOME$", "jrt:///Users/user123"),
      BasePath("jrt:/\$USER_HOME$", "jrt://Users/user123"),
      BasePath("jrt:\$USER_HOME$", "jrt:/Users/user123"),
    )
    assertEquals(expectedBasePathsList, urlRelativizer.basePaths)
  }

  companion object ParametersForParameterizedTests {

    @JvmStatic
    private fun `base paths for checking conversion`(): Stream<Arguments> =
      Stream.of(
        // Arguments.of(absolutePath1, relativePath, absolutePath2)
        Arguments.of("/Users/user123", "\$USER_HOME$", "C:/Users/otherUser"),
        Arguments.of("/Users/user123/", "\$USER_HOME$/", "C:/Users/otherUser/"),
        Arguments.of("", "", ""),
        Arguments.of("/", "/", "/"),
        Arguments.of("/some/unknown/path", "/some/unknown/path", "/some/unknown/path"),
        Arguments.of("/Users/user123/projec", "\$USER_HOME$/projec", "C:/Users/otherUser/projec"),
        Arguments.of("/Users/user123/project.txt", "\$USER_HOME$/project.txt", "C:/Users/otherUser/project.txt"),
        Arguments.of("/Users/user123/project", "\$PROJECT_DIR$", "C:/Project"),
        Arguments.of("/Users/user123/project/abc/../file.txt", "\$PROJECT_DIR$/abc/../file.txt",
                     "C:/Project/abc/../file.txt"),
        Arguments.of("file:/Users/user123/project/src/main.cpp", "file:\$PROJECT_DIR$/src/main.cpp",
                     "file:C:/Project/src/main.cpp"),
        Arguments.of("jar:///Users/user123/project/folder/x/y.txt", "jar://\$FOLDER_INSIDE_PROJECT$/x/y.txt",
                     "jar://C:/Project/folder/x/y.txt"),
        Arguments.of("file:////Users/user123", "file:////Users/user123",
                     "file:////Users/user123"),
        Arguments.of("/Users/.external", "\$EXTERNAL_DIR$", "C:/Users/otherUser/Downloads"),
        Arguments.of("file://Users/.external/folder/file.txt", "file:/\$EXTERNAL_DIR$/folder/file.txt",
                     "file:/C:/Users/otherUser/Downloads/folder/file.txt"),
        Arguments.of("/Users/user123/Users/user123", "\$USER_HOME$/Users/user123",
                     "C:/Users/otherUser/Users/user123")
      )
  }

}