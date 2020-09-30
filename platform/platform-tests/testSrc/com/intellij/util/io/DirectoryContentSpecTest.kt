// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.io

import org.assertj.core.api.Assertions
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Path
import kotlin.test.fail

class DirectoryContentSpecTest {
  @Test
  fun `files in directory`() {
    val dir = directoryContent {
      file("a.txt")
      file("b.txt")
    }.generateInTempDir()

    dir.assertMatches(directoryContent {
      file("a.txt")
      file("b.txt")
    })

    dir.assertNotMatches(directoryContent {
      file("a.txt")
    }, FileTextMatcher.ignoreBlankLines())

    dir.assertNotMatches(directoryContent {
      file("a.txt")
      file("b.txt")
      file("c.txt")
    }, FileTextMatcher.ignoreBlankLines())
  }

  @Test
  fun `directory in directory`() {
    val dir = directoryContent {
      dir("a") {
        file("a.txt")
      }
    }.generateInTempDir()

    dir.assertMatches(directoryContent {
      dir("a") {
        file("a.txt")
      }
    })

    dir.assertNotMatches(directoryContent {
      dir("b") {
        file("a.txt")
      }
    }, FileTextMatcher.ignoreBlankLines())

    dir.assertNotMatches(directoryContent {
      dir("a") {
        file("b.txt")
      }
    }, FileTextMatcher.ignoreBlankLines())
  }

  @Test
  fun `file content`() {
    val dir = directoryContent {
      file("a.txt", "text")
    }.generateInTempDir()

    dir.assertMatches(directoryContent {
      file("a.txt", "text")
    })

    dir.assertMatches(directoryContent {
      file("a.txt")
    })

    dir.assertNotMatches(directoryContent {
      file("a.txt", "a")
    }, FileTextMatcher.ignoreBlankLines())
  }

  @Test
  fun `file content with ignore empty lines option`() {
    val dir = directoryContent {
      file("a.txt", "a\n\nb")
    }.generateInTempDir()

    dir.assertMatches(directoryContent {
      file("a.txt", "a\n\nb")
    }, FileTextMatcher.ignoreBlankLines())
    dir.assertMatches(directoryContent {
      file("a.txt", "a\nb")
    }, FileTextMatcher.ignoreBlankLines())
    dir.assertMatches(directoryContent {
      file("a.txt", "a\nb\n")
    }, FileTextMatcher.ignoreBlankLines())

    dir.assertNotMatches(directoryContent {
      file("a.txt", "a\nb\nc")
    }, FileTextMatcher.ignoreBlankLines())
  }

  @Test
  fun `file in zip`() {
    val dir = directoryContent {
      zip("a.zip") {
        file("a.txt", "text")
      }
    }.generateInTempDir()

    dir.assertMatches(directoryContent {
      zip("a.zip") {
        file("a.txt", "text")
      }
    })

    dir.assertNotMatches(directoryContent {
      dir("a.zip") {
        file("a.txt", "text")
      }
    }, FileTextMatcher.ignoreBlankLines())

    dir.assertNotMatches(directoryContent {
      zip("a.zip") {
        file("a.txt", "a")
      }
    }, FileTextMatcher.ignoreBlankLines())
  }

  @Test
  fun `merge directory definitions`() {
    val dir = directoryContent {
      dir("foo") {
        file("a.txt")
      }
      dir("foo") {
        file("b.txt")
      }
    }.generateInTempDir()

    dir.assertMatches(directoryContent {
      dir("foo") {
        file("a.txt")
        file("b.txt")
      }
    })
  }

  @Test
  fun `zip file`() {
    val zip = zipFile {
      file("a.txt", "a")
    }.generateInTempDir()
    assertTrue(zip.isFile())
    Assertions.assertThat(zip.fileName.toString()).endsWith(".zip")
    zip.assertMatches(zipFile {
      file("a.txt", "a")
    })
    zip.assertNotMatches(zipFile {
      file("b.txt", "a")
    }, FileTextMatcher.ignoreBlankLines())
    zip.assertNotMatches(zipFile {
      file("a.txt", "b")
    }, FileTextMatcher.ignoreBlankLines())
    zip.assertNotMatches(directoryContent {
      file("a.txt", "b")
    }, FileTextMatcher.ignoreBlankLines())
  }
}

private fun Path.assertNotMatches(spec: DirectoryContentSpec, fileTextMatcher: FileTextMatcher = FileTextMatcher.exact()) {
  try {
    assertMatches(spec, fileTextMatcher)
    fail("File matches to spec but it must not")
  }
  catch (ignored: AssertionError) {
  }
}