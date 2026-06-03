// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.eel

import com.intellij.platform.eel.EelDescriptor
import com.intellij.platform.eel.EelOsFamily
import com.intellij.platform.eel.path.EelPath
import com.intellij.platform.eel.provider.utils.WindowsPathUtils
import com.intellij.util.system.OS
import org.jetbrains.annotations.NonNls
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
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

  @Test
  fun `expandPerDriveRoots inserts drive zone when mount has no marker`() {
    val roots = WindowsPathUtils.expandPerDriveRoots($$"""/$tcp.ij/abc""")
    assertEquals(26, roots.size)
    assertEquals($$"""/$tcp.ij/abc/@/A""", roots.first())
    assertEquals($$"""/$tcp.ij/abc/@/Z""", roots.last())
  }

  @Test
  fun `expandPerDriveRoots reuses drive zone when mount already ends with it`() {
    val roots = WindowsPathUtils.expandPerDriveRoots($$"""/$tcp.ij/abc/@""")
    assertEquals(26, roots.size)
    assertEquals($$"""/$tcp.ij/abc/@/A""", roots.first())
    assertEquals($$"""/$tcp.ij/abc/@/Z""", roots.last())
  }

  @Test
  fun `extractUncRoot returns null for drive letter paths`() {
    assertNull(WindowsPathUtils.extractUncRoot($$"""/$tcp.ij/abc/@""", $$"""/$tcp.ij/abc/@/C/Users"""))
    assertNull(WindowsPathUtils.extractUncRoot($$"""/$tcp.ij/abc""", $$"""/$tcp.ij/abc/@/C/Users"""))
  }

  @Test
  fun `extractUncRoot returns server-share for UNC under mount with marker`() {
    assertEquals(
      $$"""/$tcp.ij/abc/@/server/share""",
      WindowsPathUtils.extractUncRoot($$"""/$tcp.ij/abc/@""", $$"""/$tcp.ij/abc/@/server/share/dir/file"""),
    )
  }

  @Test
  fun `extractUncRoot returns server-share for UNC under mount without marker`() {
    assertEquals(
      $$"""/$tcp.ij/abc/server/share""",
      WindowsPathUtils.extractUncRoot($$"""/$tcp.ij/abc""", $$"""/$tcp.ij/abc/server/share/dir/file"""),
    )
  }

  @Test
  fun `extractUncRoot returns null for path outside mount`() {
    assertNull(WindowsPathUtils.extractUncRoot($$"""/$tcp.ij/abc/@""", $$"""/$tcp.ij/xyz/@/server/share"""))
  }

  @Test
  fun `extractUncRoot returns null when share segment is missing`() {
    assertNull(WindowsPathUtils.extractUncRoot($$"""/$tcp.ij/abc/@""", $$"""/$tcp.ij/abc/@/server"""))
  }

  @Test
  fun `extractUncRoot rejects path that only shares string prefix with mount`() {
    assertNull(WindowsPathUtils.extractUncRoot($$"""/$tcp.ij/abc""", $$"""/$tcp.ij/abcdef/server/share"""))
  }

  @Test
  fun `expandPerDriveRoots trims trailing slash on mount`() {
    val roots = WindowsPathUtils.expandPerDriveRoots($$"""/$tcp.ij/abc/@/""")
    assertEquals(26, roots.size)
    assertEquals($$"""/$tcp.ij/abc/@/A""", roots.first())
    assertEquals($$"""/$tcp.ij/abc/@/Z""", roots.last())
  }

  @Test
  fun `expandPerDriveRoots trims trailing slash on mount without marker`() {
    val roots = WindowsPathUtils.expandPerDriveRoots($$"""/$tcp.ij/abc/""")
    assertEquals(26, roots.size)
    assertEquals($$"""/$tcp.ij/abc/@/A""", roots.first())
    assertEquals($$"""/$tcp.ij/abc/@/Z""", roots.last())
  }

  @Test
  fun `expandPerDriveRoots normalizes backslashes on Windows-host mount`() {
    val roots = WindowsPathUtils.expandPerDriveRoots("""\\tcp.ij\abc""")
    assertEquals(26, roots.size)
    assertEquals("//tcp.ij/abc/@/A", roots.first())
    assertEquals("//tcp.ij/abc/@/Z", roots.last())
  }

  @Test
  fun `extractUncRoot accepts mount with trailing slash`() {
    assertEquals(
      $$"""/$tcp.ij/abc/@/server/share""",
      WindowsPathUtils.extractUncRoot($$"""/$tcp.ij/abc/@/""", $$"""/$tcp.ij/abc/@/server/share/dir/file"""),
    )
  }

  @Test
  fun `extractUncRoot accepts backslash-separated mount`() {
    assertEquals(
      "//tcp.ij/abc/server/share",
      WindowsPathUtils.extractUncRoot("""\\tcp.ij\abc""", "//tcp.ij/abc/server/share/dir/file"),
    )
  }
}
