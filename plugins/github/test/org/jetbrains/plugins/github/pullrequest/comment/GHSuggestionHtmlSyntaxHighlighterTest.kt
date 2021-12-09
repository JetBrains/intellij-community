// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.pullrequest.comment

import org.junit.Assert.assertEquals
import org.junit.Test

class GHSuggestionHtmlSyntaxHighlighterTest {

  @Test
  fun `cut multiple lines from diff hunk`() {
    val diffHunk = """
      @@ -0,0 +1,111 @@
      +class MultipleDiffHunk {                   // line number: 0
      +                                           // line number: 1
      +                                           // line number: 2 
      +                                           // line number: 3
      +                                           // line number: 4
      +                                           // line number: 5
      +                                           // line number: 6
      +                                           // line number: 7
      +    companion object {                     // line number: 8
      +        @JvmStatic
      +        fun main(args: Array<String>) {
      +            println("MultipleDiffHunk")
      +        }
      +    }                                      // line number: 13
    """.trimIndent()

    val expectedDiffHunk = """
      @JvmStatic
      fun main(args: Array<String>) {
          println("MultipleDiffHunk")
      }
    """.trimIndent()

    assertEquals(expectedDiffHunk, processDiffHunk(diffHunk, 9, 12))
  }

  @Test
  fun `cut single line from diff hunk`() {
    val diffHunk = """
      @@ -0,0 +1,111 @@
      +class SingleDiffHunk {                     // line number: 0
      +    companion object {                     // line number: 1
      +        @JvmStatic                         // line number: 2
      +        fun main(args: Array<String>) {    // line number: 3
      +            println("SingleDiffHunk")
    """.trimIndent()

    val expectedDiffHunk = """
      println("SingleDiffHunk")
    """.trimIndent()

    assertEquals(expectedDiffHunk, processDiffHunk(diffHunk, 4, 4))
  }

  @Test
  fun `cut single line from edited diff hunk`() {
    val diffHunk = """
      @@ -1,10 +1,12 @@
       class Apple {
      -    private var counter = 0
      +    private var counter = 42
       
           companion object {
               @JvmStatic
               fun main(args: Array<String>) {
      -            println(Companion::class.java.simpleName)
      +            println(Companion::class.java.name)
    """.trimIndent()

    val expectedDiffHunk = """
          println(Companion::class.java.name)
    """.trimIndent()

    assertEquals(expectedDiffHunk, processDiffHunk(diffHunk, 8, 8))
  }

  @Test
  fun `cut multiple lines from edited diff hunk`() {
    val diffHunk = """
      @@ -1,111 +1,111 @@
      -class A {
      -    private var counter = 0
      +class B {
      +    private var counter = 1
      +    private val name = "diffHunk"
    """.trimIndent()

    val expectedDiffHunk = """
      class B {
          private var counter = 1
          private val name = "diffHunk"
    """.trimIndent()

    assertEquals(expectedDiffHunk, processDiffHunk(diffHunk, 0, 4))
  }

  private fun processDiffHunk(diffHunk: String, startLine: Int, endLine: Int): String {
    return GHSuggestionHtmlSyntaxHighlighter.trimStartWithMinIndent(
      GHSuggestionHtmlSyntaxHighlighter.cutOriginalHunk(diffHunk, startLine, endLine)
    )
  }
}