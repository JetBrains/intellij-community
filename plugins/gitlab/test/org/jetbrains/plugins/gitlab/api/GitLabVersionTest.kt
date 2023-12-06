// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.api

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class GitLabVersionTest {
  @Test
  fun `compareTo 14_0 is less than 14_1`() {
    assertEquals(-1, GitLabVersion(14, 0).compareTo(GitLabVersion(14, 1)))
  }

  @Test
  fun `compareTo 14_0 is less than 14_1_0`() {
    assertEquals(-1, GitLabVersion(14, 0).compareTo(GitLabVersion(14, 1, 0)))
  }

  @Test
  fun `compareTo 14_1 is greater than 14_0_0`() {
    assertEquals(1, GitLabVersion(14, 1, 0).compareTo(GitLabVersion(14, 0)))
  }

  @Test
  fun `compareTo 14 is equal to 14_0_0`() {
    assertEquals(0, GitLabVersion(14).compareTo(GitLabVersion(14, 0, 0)))
  }

  @Test
  fun `compareTo 15_0_0 is less than 15_7`() {
    assertEquals(-1, GitLabVersion(15, 0, 0).compareTo(GitLabVersion(15, 7)))
  }
}