// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.workspaceModel.ide

import com.intellij.openapi.util.io.FileUtilRt
import com.intellij.testFramework.ApplicationRule
import com.intellij.testFramework.DisposableRule
import com.intellij.platform.workspace.jps.serialization.impl.ApplicationLevelUrlRelativizer
import org.jetbrains.jps.model.serialization.PathMacroUtil
import org.junit.ClassRule
import org.junit.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import java.io.File
import java.util.stream.Stream
import kotlin.test.assertEquals

class ApplicationLevelUrlRelativizerTest {

  val disposableRule = DisposableRule()
  private val urlRelativizer = ApplicationLevelUrlRelativizer(insideIdeProcess = true)

  @ParameterizedTest
  @MethodSource("paths to test")
  fun `check conversion`(absoluteUrl: String, relativeUrl: String) {
    // Check the conversion both ways (absolute to relative, and relative to absolute)
    assertEquals(relativeUrl, urlRelativizer.toRelativeUrl(absoluteUrl))
    assertEquals(absoluteUrl, urlRelativizer.toAbsoluteUrl(relativeUrl))
  }

  @Test
  fun `when converted relative path should be system independent`() {
    val applicationHomeDir = PathMacroUtil.getGlobalSystemMacros()["APPLICATION_HOME_DIR"]!!
    val absoluteUrl = "$applicationHomeDir\\dir\\file.txt"
    val expectedRelativeUrl = "\$APPLICATION_HOME_DIR$/dir/file.txt"
    assertEquals(expectedRelativeUrl, urlRelativizer.toRelativeUrl(absoluteUrl))
  }

  companion object {
    @JvmField
    @ClassRule
    val applicationRule = ApplicationRule()

    @JvmStatic
    private fun `paths to test`(): Stream<Arguments> {
      val macros = PathMacroUtil.getGlobalSystemMacros()

      val applicationHomeDir = macros["APPLICATION_HOME_DIR"]!!
      val applicationConfigDir = macros["APPLICATION_CONFIG_DIR"]!!
      val applicationPluginsDir = macros["APPLICATION_PLUGINS_DIR"]!!
      val userHome = macros["USER_HOME"]!!

      val userHomeFile = File(userHome)

      // This `almostUserHome` is the same path as `userHome`, but the last part is text file instead of directory.
      // For example, if `userHome = "/Users/A"`, then `almostUserHome = /Users/A.txt`.
      val almostUserHome = "${FileUtilRt.getParentFile(userHomeFile)}/${userHomeFile.name}.txt"

      return Stream.of(
        Arguments.of("", ""),
        Arguments.of("/", "/"),
        Arguments.of("file:///random/", "file:///random/"),
        Arguments.of("file:///", "file:///"),
        Arguments.of("file:/", "file:/"),

        Arguments.of(userHome, "\$USER_HOME\$"),
        Arguments.of("$userHome/..", "\$USER_HOME\$/.."),
        Arguments.of(applicationHomeDir, "\$APPLICATION_HOME_DIR\$"),
        Arguments.of(applicationConfigDir, "\$APPLICATION_CONFIG_DIR\$"),
        Arguments.of(applicationPluginsDir, "\$APPLICATION_PLUGINS_DIR\$"),
        Arguments.of("$applicationPluginsDir/file.txt", "\$APPLICATION_PLUGINS_DIR\$/file.txt"),
        Arguments.of("$applicationPluginsDir/dir", "\$APPLICATION_PLUGINS_DIR\$/dir"),
        Arguments.of("$applicationPluginsDir/dir/file.txt", "\$APPLICATION_PLUGINS_DIR\$/dir/file.txt"),

        Arguments.of("file://$applicationHomeDir", "file://\$APPLICATION_HOME_DIR\$"),
        Arguments.of("jar://$applicationConfigDir", "jar://\$APPLICATION_CONFIG_DIR\$"),
        Arguments.of("jrt://$applicationPluginsDir", "jrt://\$APPLICATION_PLUGINS_DIR\$"),
        Arguments.of("unknown://$userHome", "unknown://$userHome"),

        // In the following few tests, absolute path is not entirely userHome, so relative path should be same as an absolute
        Arguments.of("${userHome}aaa", "${userHome}aaa"),
        Arguments.of(userHome.substring(0, userHome.length - 1), userHome.substring(0, userHome.length - 1)),
        Arguments.of(almostUserHome, almostUserHome),

        Arguments.of("$applicationHomeDir$userHome", "\$APPLICATION_HOME_DIR\$$userHome"),
        Arguments.of("file://SomeDirectory/$userHome/OtherDir/$applicationHomeDir/someFile.txt",
                 "file://SomeDirectory/$userHome/OtherDir/$applicationHomeDir/someFile.txt")
      )
    }
  }

}