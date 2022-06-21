// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.pullrequest.comment

import org.junit.Assert.assertArrayEquals
import org.junit.Test

class GHSuggestionHtmlSyntaxHighlighterTest {

  @Test
  fun `cut multiple lines from diff hunk`() {
    val afterStartLine = 0
    val afterEndLine = 14

    val diffHunk = """
      @@ -0,0 +$afterStartLine,$afterEndLine @@
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

    val expectedChangedContent = listOf(
      "class MultipleDiffHunk {                   // line number: 0",
      "                                           // line number: 1",
      "                                           // line number: 2",
      "                                           // line number: 3",
      "                                           // line number: 4",
      "                                           // line number: 5",
      "                                           // line number: 6",
      "                                           // line number: 7",
      "    companion object {                     // line number: 8",
      "        @JvmStatic",
      "        fun main(args: Array<String>) {",
      "            println(\"MultipleDiffHunk\")",
      "        }",
      "    }                                      // line number: 13"
    )

    checkSuggestedChange(expectedChangedContent, diffHunk, afterStartLine, afterEndLine - 1)
  }

  @Test
  fun `cut single line from diff hunk`() {
    val afterStartLine = 0
    val afterEndLine = 5

    val diffHunk = """
      @@ -0,0 +$afterStartLine,$afterEndLine @@
      +class SingleDiffHunk {                     // line number: 0
      +    companion object {                     // line number: 1
      +        @JvmStatic                         // line number: 2
      +        fun main(args: Array<String>) {    // line number: 3
      +            println("SingleDiffHunk")
    """.trimIndent()

    val expectedChangedContent = listOf(
      "            println(\"SingleDiffHunk\")"
    )

    checkSuggestedChange(expectedChangedContent, diffHunk, afterEndLine - 1, afterEndLine - 1)
  }

  @Test
  fun `cut single line from edited diff hunk`() {
    val afterStartLine = 0
    val afterEndLine = 8

    val diffHunk = """
      @@ -$afterStartLine,$afterEndLine +$afterStartLine,$afterEndLine @@
       class Apple {
      -    private var counter = 0
      +    private var counter = 42
       
           companion object {
               @JvmStatic
               fun main(args: Array<String>) {
      -            println(Companion::class.java.simpleName)
      +            println(Companion::class.java.name)
    """.trimIndent()

    val expectedChangedContent = listOf(
      "    companion object {",
      "        @JvmStatic",
      "        fun main(args: Array<String>) {",
      "            println(Companion::class.java.name)"
    )

    checkSuggestedChange(expectedChangedContent, diffHunk, 4, afterEndLine - 1)
  }

  @Test
  fun `cut multiple lines from edited diff hunk`() {
    val afterStartLine = 0
    val afterEndLine = 4

    val diffHunk = """
      @@ -0,3 +$afterStartLine,$afterEndLine @@
      -class A {
      -    private var counter = 0
      +class B {
      +    private var counter = 1
      +    private val name = "diffHunk"
    """.trimIndent()

    val expectedChangedContent = listOf(
      "class B {",
      "    private var counter = 1",
      "    private val name = \"diffHunk\""
    )

    checkSuggestedChange(expectedChangedContent, diffHunk, afterStartLine, afterEndLine - 1)
  }

  private fun checkSuggestedChange(expected: List<String>, diffHunk: String, afterStartLine: Int, afterEndLine: Int) {
    val suggestedChangeInfo = GHSuggestedChange.create("", diffHunk, "", afterStartLine, afterEndLine)
    val changedContent = suggestedChangeInfo.cutChangedContent()

    assertArrayEquals(expected.toTypedArray(), changedContent.toTypedArray())
  }
}