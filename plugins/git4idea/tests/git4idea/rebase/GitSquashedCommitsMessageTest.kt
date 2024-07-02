// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.rebase

import junit.framework.TestCase.assertEquals
import org.junit.Test

class GitSquashedCommitsMessageTest {
  @Test
  fun `squashed message doesn't contain duplicates`() {
    val messages = listOf("fix", "fix", "fix!!!", "fix")
    assertEquals("""
      fix
      
      
      fix!!!
    """.trimIndent(), GitSquashedCommitsMessage.prettySquash(messages))
  }

  @Test
  fun `autosquash subjects are removed`() {
    val messages = listOf(
      """
        amend! test commit
        
        amend message
      """.trimIndent(),
      """
        squash! test commit
        
        squash message
      """.trimIndent(),
      "fixup! test commit",
      "test commit"
    )
    assertEquals("""
      amend message
      
      
      squash message
      
      
      test commit
    """.trimIndent(), GitSquashedCommitsMessage.prettySquash(messages))
  }

  @Test
  fun `autosquash 2 fixup commits`() {
    val messages = listOf("fixup! msg", "fixup! msg")
    assertEquals("fixup! msg", GitSquashedCommitsMessage.prettySquash(messages))
  }

  @Test
  fun `autosquash 2 fixup commits and real commit`() {
    val messages = listOf("fixup! msg", "fixup! msg", "msg")
    assertEquals("msg", GitSquashedCommitsMessage.prettySquash(messages))
  }

  @Test
  fun `autosquash subjects is not removed if target commit is missing`() {
    val messages = listOf("???", "fixup! msg")
    assertEquals("""
      ???
      
      
      fixup! msg
    """.trimIndent(), GitSquashedCommitsMessage.prettySquash(messages))
  }
}