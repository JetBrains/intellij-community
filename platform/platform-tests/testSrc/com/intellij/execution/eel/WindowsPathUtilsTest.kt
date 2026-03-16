// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.eel

import com.intellij.platform.eel.EelDescriptor
import com.intellij.platform.eel.EelOsFamily
import com.intellij.platform.eel.path.EelPath
import com.intellij.platform.eel.provider.utils.WindowsPathUtils
import com.intellij.util.system.OS
import org.jetbrains.annotations.NonNls
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import java.io.File
import kotlin.io.path.Path

class WindowsPathUtilsTest {

  class TestEelDescriptor(override val osFamily: EelOsFamily) : EelDescriptor {
    override val name: @NonNls String get() = "Test Descriptor"
  }

  @ParameterizedTest
  @CsvSource(
    textBlock = """
      C:\Users\test,        @\C\Users\test
      C:\,                  @\C\
      C:,                   @\C
      D:\Program Files,     @\D\Program Files
      \\server\share\dir,   server\share\dir
      \\server\share\dir\,  server\share\dir\
      \\server\share\,      server\share\
      C:\Users\test,        @\C\Users\test
      C:\,                  @\C\
      C:,                   @\C
      \\server\share\dir,   server\share\dir"""
  )
  fun `test resolveEelPathOntoRoot`(eelPathString: String, expected: String) {
    val rootPath = if (OS.CURRENT == OS.Windows) {
      Path("\\\\virtual.ij\\mount@")
    }
    else {
      Path($$"""/$virtual.ij/mount@""")
    }
    val eelPath = EelPath.parse(eelPathString, TestEelDescriptor(EelOsFamily.Windows))
    val result = WindowsPathUtils.resolveEelPathOntoRoot(rootPath, eelPath)
    assertEquals(rootPath.resolve(expected.replace("\\", File.separator)), result)
  }

  @ParameterizedTest
  @CsvSource(
    textBlock = """
      @/C,                               C:,                    ''
      @/C/Users,                         C:,                    Users
      @/D/Program Files,                 D:,                    Program Files
      @/D/Program Files/JetBrains,       D:,                    Program Files/JetBrains
      server.share/dir,                  \\server.share\dir,    ''
      server.share/dir/tmp,              \\server.share\dir,    tmp
      server.share/dir/tmp/nested/file,  \\server.share\dir,    tmp/nested/file"""
  )
  fun `test rootRelativeToEelPath with Path parameter`(relativePath: String, expectedRoot: String, expectedSubpath: String) {
    assertEquals("$expectedRoot${if (expectedSubpath.isEmpty()) "" else "/$expectedSubpath"}".replace("\\", "/"), WindowsPathUtils.rootRelativeToEelPath(relativePath))
    assertEquals(expectedRoot to Path(expectedSubpath), WindowsPathUtils.rootRelativeToEelPath(Path(relativePath.replace("/", File.separator))))
  }
}
