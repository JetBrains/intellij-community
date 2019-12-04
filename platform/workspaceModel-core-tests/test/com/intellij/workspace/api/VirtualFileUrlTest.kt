package com.intellij.workspace.api

import com.intellij.openapi.vfs.VfsUtil
import org.junit.Assert
import org.junit.Test

class VirtualFileUrlTest {
  @Test
  fun testRoundTrip() {
    roundTrip("")
    roundTrip("/")
    roundTrip("foobar")
    roundTrip("file:///a")
    roundTrip("file:///")
    roundTrip("file://")
    roundTrip("file:////")
    roundTrip("file:///a/")
    roundTrip("jar://C:/Users/X/.m2/repository/org/jetbrains/intellij/deps/jdom/2.0.6/jdom-2.0.6.jar")
    roundTrip("jar://C:/Users/X/.m2/repository/org/jetbrains/intellij/deps/jdom/2.0.6/jdom-2.0.6.jar!/")
    roundTrip("jar://C:/Users/X/.m2/repository/org/jetbrains/intellij/deps/jdom/2.0.6/jdom-2.0.6.jar!//")
  }

  @Test
  fun testIsEqualOrParentOf() {
    assertIsEqualOrParentOf(true, "temp:///src", "temp:///src/my")
    assertIsEqualOrParentOf(true, "temp:///src", "temp:///src/my/")
    assertIsEqualOrParentOf(false, "temp:///src", "temp:///srC/my")
    assertIsEqualOrParentOf(false, "temp:///src/x", "temp:///src/y")
    assertIsEqualOrParentOf(false, "file:///src/my", "temp:///src/my")
    assertIsEqualOrParentOf(false, "file:///src/my", "temp:///src/my")
    assertIsEqualOrParentOf(false, "", "temp:///src/my")
    assertIsEqualOrParentOf(false, "temp:///src/my", "")
    assertIsEqualOrParentOf(true, "temp://", "temp:///src/my")
  }

  @Test
  fun testFilePath() {
    assertFilePath(null, "jar:///main/a.jar!/my/class.class")
    assertFilePath("/main/a.jar", "jar:///main/a.jar!/")
    assertFilePath("/main/a.jar", "jar:///main/a.jar!")
    assertFilePath("/main/a.jar", "jar:///main/a.jar")
    assertFilePath("/main/a.jar", "file:///main/a.jar")
    assertFilePath(null, "")
  }

  @Test
  fun testFromPath() {
    Assert.assertEquals("", VirtualFileUrlManager.fromPath("").url)

    fun assertUrlFromPath(path: String) {
      Assert.assertEquals(VfsUtil.pathToUrl(path), VirtualFileUrlManager.fromPath(path).url)
    }

    assertUrlFromPath("/main/a.jar")
    assertUrlFromPath("C:\\main\\a.jar")
    assertUrlFromPath("/main/a.jar!/")
    assertUrlFromPath("/main/a.jar!/a.class")
  }

  @Test
  fun normalizeSlashes() {
    Assert.assertEquals("jar://C:/Users/X/a.txt", VirtualFileUrlManager.fromUrl("jar://C:/Users\\X\\a.txt").url)
  }

  private fun assertFilePath(expectedResult: String?, url: String) {
    Assert.assertEquals(expectedResult, VirtualFileUrlManager.fromUrl(url).filePath)
  }

  private fun assertIsEqualOrParentOf(expectedResult: Boolean, parentString: String, childString: String) {
    val parent = VirtualFileUrlManager.fromUrl(parentString)
    val child = VirtualFileUrlManager.fromUrl(childString)
    Assert.assertTrue("'$parent'.isEqualOrParentOf('$parent')", parent.isEqualOrParentOf(parent))
    Assert.assertTrue("'$child'.isEqualOrParentOf('$child')", child.isEqualOrParentOf(child))
    Assert.assertEquals(
      "'$parent'.isEqualOrParentOf('$child') should be ${if (expectedResult) "true" else "false"}",
      expectedResult,
      parent.isEqualOrParentOf(child))
  }

  private fun roundTrip(url: String) {
    Assert.assertEquals(url, VirtualFileUrlManager.fromUrl(url).url)
  }
}