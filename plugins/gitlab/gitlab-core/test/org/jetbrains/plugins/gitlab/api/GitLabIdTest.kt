// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.api

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class GitLabIdTest {
  @Test
  // Sanity check because I kept getting problems with equality it seemed
  fun `equal GitLabGidData are equal`() {
    assertEquals(GitLabGidData("gid://gitlab/Discussions/62783627"), GitLabGidData("gid://gitlab/Discussions/62783627"))
  }

  @Test
  // Sanity check because I kept getting problems with equality it seemed
  fun `equal GitLabRestIdData are equal`() {
    assertEquals(GitLabRestIdData("62783627", "Discussions"), GitLabRestIdData("62783627", "Discussions"))
  }

  @Test
  // Sanity check because I kept getting problems with equality it seemed
  fun `GitLabRestIdData with different domains are unequal`() {
    assertNotEquals(GitLabRestIdData("62783627", null), GitLabRestIdData("62783627", "Discussions"))
  }

  @Test
  fun `GitLabRestId guesses an expected GID with domain`() {
    assertEquals("gid://gitlab/Note/783273", GitLabRestIdData("783273", "Note").guessGid())
  }

  @Test
  fun `GitLabRestId guesses null without domain`() {
    assertNull(GitLabRestIdData("783273").guessGid())
  }

  @Test
  fun `GitLabGid guesses GID from itself`() {
    assertEquals("gid://gitlab/Note/783273", GitLabGidData("gid://gitlab/Note/783273").guessGid())
  }

  @Test
  fun `GitLabGid guesses rest ID from last part`() {
    assertEquals("783273", GitLabGidData("gid://gitlab/Note/783273").guessRestId())
  }

  @Test
  fun `GitLabRestId guesses rest ID from itself`() {
    assertEquals("783273", GitLabRestIdData("783273", "Note").guessRestId())
  }
}