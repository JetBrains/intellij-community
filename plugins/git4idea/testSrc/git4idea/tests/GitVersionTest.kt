// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.tests

import com.intellij.openapi.util.SystemInfo
import git4idea.config.GitVersion
import git4idea.config.GitVersion.Type
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * @author Kirill Likhodedov
 */
class GitVersionTest {

  /**
   * Tests that parsing the 'git version' command output returns the correct GitVersion object.
   * Tests on UNIX and on Windows - both CYGWIN and MSYS.
   */
  @Test
  fun testParse() {
    if (SystemInfo.isUnix) {
      for (test in commonTests) {
        val version = GitVersion.parse(test.output)
        assertEqualVersions(version, test, Type.UNIX)
      }
    }
    else if (SystemInfo.isWindows) {
      for (test in commonTests) {
        val version = GitVersion.parse(test.output)
        assertEqualVersions(version, test, Type.CYGWIN)
      }

      for (test in msysTests) {
        val version = GitVersion.parse(test.output)
        assertEqualVersions(version, test, Type.MSYS)
      }
    }
  }

  @Test
  fun testEqualsAndCompare() {
    val v1 = GitVersion.parse("git version 1.6.3")
    val v2 = GitVersion.parse("git version 1.7.3")
    val v3 = GitVersion.parse("git version 1.7.3")
    val v4 = GitVersion.parse("git version 1.7.3.msysgit.0")
    assertFalse(v1 == v2)
    assertFalse(v1 == v3)
    assertTrue(v2 == v3)
    if (SystemInfo.isWindows) {
      assertFalse(v1 == v4)
      assertFalse(v2 == v4)
      assertFalse(v3 == v4)
    }

    assertEquals(-1, v1.compareTo(v2))
    assertEquals(-1, v1.compareTo(v3))
    assertEquals(-1, v1.compareTo(v4))
    assertEquals(0, v2.compareTo(v3))
    assertEquals(0, v2.compareTo(v4))
    assertEquals(0, v3.compareTo(v4))
  }

  companion object {
    private val commonTests = listOf(
      TestGitVersion("git version 1.6.4", 1, 6, 4, 0),
      TestGitVersion("git version 1.7.3.3", 1, 7, 3, 3),
      TestGitVersion("git version 1.7.3.5.gb27be", 1, 7, 3, 5),
      TestGitVersion("git version 1.7.4-rc1", 1, 7, 4, 0),
      TestGitVersion("git version 1.7.7.5 (Apple Git-24)", 1, 7, 7, 5),
      TestGitVersion("git version 1.9.rc0.143.g6fd479e", 1, 9, 0, 0),
    )

    private val msysTests = listOf(
      TestGitVersion("git version 1.6.4.msysgit.0", 1, 6, 4, 0),
      TestGitVersion("git version 1.7.3.3.msysgit.1", 1, 7, 3, 3),
      TestGitVersion("git version 1.7.3.2.msysgit", 1, 7, 3, 2),
      TestGitVersion("git version 1.7.3.5.msysgit.gb27be", 1, 7, 3, 5),
      TestGitVersion("git version 2.19.1.windows.1", 2, 19, 1, 0),
    )

    // Compares the parsed output and what we've expected.
    // Uses reflection to get private fields of GitVersion: we don't need them in code, so no need to trash the class with unused accessors.
    private fun assertEqualVersions(actual: GitVersion, expected: TestGitVersion, expectedType: Type) {
      val majorField = GitVersion::class.java.getDeclaredField("myMajor").apply { isAccessible = true }
      val major = majorField.getInt(actual)
      val minorField = GitVersion::class.java.getDeclaredField("myMinor").apply { isAccessible = true }
      val minor = minorField.getInt(actual)
      val revField = GitVersion::class.java.getDeclaredField("myRevision").apply { isAccessible = true }
      val rev = revField.getInt(actual)
      val patchField = GitVersion::class.java.getDeclaredField("myPatchLevel").apply { isAccessible = true }
      val patch = patchField.getInt(actual)
      val typeField = GitVersion::class.java.getDeclaredField("myType").apply { isAccessible = true }
      val type = typeField.get(actual) as Type

      assertEquals(expected.major, major)
      assertEquals(expected.minor, minor)
      assertEquals(expected.rev, rev)
      assertEquals(expected.patch, patch)
      assertEquals(expectedType, type)
    }
  }

  private data class TestGitVersion(
    val output: String,
    val major: Int,
    val minor: Int,
    val rev: Int,
    val patch: Int,
  )
}
