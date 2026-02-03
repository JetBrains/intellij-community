// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.git.terminal

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class GitAliasContributorTest {
  @Test
  fun `alias parsing works for simple subcommand replacement`() {
    assertEquals(
      "st" to "status",
      parseGitAlias("alias.st status")
    )
  }

  @Test
  fun `alias parsing works for simple subcommand replacement with arguments`() {
    assertEquals(
      "plog" to "log --format=pretty",
      parseGitAlias("alias.plog log --format=pretty")
    )
  }

  @Test
  fun `alias parsing works for full replacement`() {
    assertEquals(
      "ls" to "ls",
      parseGitAlias("alias.ls !ls")
    )
  }

  @Test
  fun `alias parsing works for full replacement with arguments`() {
    assertEquals(
      "ls" to "ls -l -a",
      parseGitAlias("alias.ls !ls -l -a")
    )
  }

  @Test
  fun `alias parsing works for full replacement does something sensible for function replacement`() {
    assertEquals(
      "helloworld" to "f() { \\ \n echo this is silly \\ \n }; f",
      parseGitAlias("alias.helloworld !f() { \\ \n echo this is silly \\ \n }; f")
    )
  }
}