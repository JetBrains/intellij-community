/*
 * Copyright 2000-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.util.io

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
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
    assertTrue(zip.isFile)
    assertEquals("zip", zip.extension)
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

private fun File.assertNotMatches(spec: DirectoryContentSpec, fileTextMatcher: FileTextMatcher = FileTextMatcher.exact()) {
  try {
    assertMatches(spec, fileTextMatcher)
    fail("File matches to spec but it must not")
  }
  catch (ignored: AssertionError) {
  }
}