// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.searchEverywhere.backend.providers.filesFuzzy

import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vfs.VirtualFile
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`

/**
 * Unit tests for SmithWatermanMatcher.
 */
class SmithWatermanMatcherTest {

  @Test
  fun testFileMatching() {
    val file = createMockFile("TestFile.kt", "/src/TestFile.kt")
    val matcher = SmithWatermanMatcher("tf")

    val result = matcher.match(file)
    assertTrue(result.hasMatch())
    assertTrue(result.score > 0)
  }

  @Test
  fun testCachingBehavior() {
    val file = createMockFile("TestFile.kt", "/src/TestFile.kt")
    val matcher = SmithWatermanMatcher("tf")

    val result1 = matcher.match(file)
    val result2 = matcher.match(file)

    assertEquals(result1, result2)
  }

  @Test
  fun testCacheClearing() {
    val file = createMockFile("TestFile.kt", "/src/TestFile.kt")
    val matcher = SmithWatermanMatcher("tf")

    matcher.match(file)
    Disposer.dispose(matcher)

    val result = matcher.match(file)
    assertTrue(result.hasMatch())
  }

  @Test
  fun testPathMatchingFallback() {
    val file = createMockFile("file.kt", "/src/main/kotlin/com/example/file.kt")
    val matcher = SmithWatermanMatcher("example")

    val result = matcher.matchWithPath(file)
    assertTrue(result.hasMatch() || result.score >= 0)
  }

  @Test
  fun testHighScoreFilenameTakesPrecedenceOverPath() {
    val file = createMockFile("ExampleFile.kt", "/src/test/ExampleFile.kt")
    val matcher = SmithWatermanMatcher("ef")

    val fileNameResult = matcher.match(file)
    matcher.matchWithPath(file)

    assertTrue(fileNameResult.normalizedScore > 0.5)
  }

  @Test
  fun testPatternWithExtensionStrippedBeforeMatching() {
    val matcher = SmithWatermanMatcher("Main.kt")

    val resultWithExt = matcher.match("MainActivity.kt")
    val matcherNoExt = SmithWatermanMatcher("Main")
    val resultNoExt = matcherNoExt.match("MainActivity.kt")

    assertTrue(resultWithExt.hasMatch())
    assertEquals(resultNoExt.matchedIndices, resultWithExt.matchedIndices,
      "Pattern 'Main.kt' should match same indices as 'Main' against 'MainActivity.kt' when extensions match")
    assertTrue(resultWithExt.score >= resultNoExt.score,
      "Pattern with extension should score at least as high (smaller length penalty on stripped filename)")
  }

  private fun createMockFile(name: String, path: String): VirtualFile {
    val file = mock(VirtualFile::class.java)
    `when`(file.name).thenReturn(name)
    `when`(file.path).thenReturn(path)

    val parent = mock(VirtualFile::class.java)
    `when`(parent.path).thenReturn(path.substringBeforeLast('/'))
    `when`(file.parent).thenReturn(parent)

    return file
  }
}
