// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.fileEditor.impl

import com.intellij.diff.comparison.CancellationChecker
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.editor.ex.DocumentOp
import com.intellij.openapi.editor.ex.DocumentText
import com.intellij.openapi.editor.ex.applyOp
import com.intellij.openapi.editor.ex.experimental.assertSameText
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path
import java.util.Random

/**
 * Tests the round trip of [DocTextDiff].
 *
 * Every test follows one use case. A base [DocumentText] changes to the version 1 through a known op
 * list. [DocTextDiff] then reads only the base and the version 1, and returns its own op list. The
 * version 2 applies that list to the base. The version 2 must equal the version 1.
 *
 * The recovered list is not the known list. The two lists only have to agree on the result. So each
 * test states four things in full: the base, the ops that build the version 1, the version 1, and
 * the recovered script. The reader sees the diff and the answer side by side, with no offset to
 * count. [assertDiff] checks all four.
 */
class DocTextDiffTest {

  @Test
  fun `an unchanged text yields no ops`() {
    val base = document(
      """
      fun main() {
        println()
      }
      """
    )
    assertDiff(
      base = base,
      ops = emptyList(),
      version1 = base,
      script = "",
    )
  }

  @Test
  fun `an insert at the start becomes one op`() {
    val base = document(
      """
      fun main() {
        println()
      }
      """
    )
    assertDiff(
      base = base,
      ops = listOf(insertBefore(base, "fun", "private ")),
      version1 = document(
        """
        private fun main() {
          println()
        }
        """
      ),
      script = """
        ins 0 "private "
      """,
    )
  }

  @Test
  fun `an insert at the end becomes one op`() {
    val base = document(
      """
      fun main() {
        println()
      }
      """
    )
    assertDiff(
      base = base,
      ops = listOf(append(base, "// end\n")),
      version1 = document(
        """
        fun main() {
          println()
        }
        // end
        """
      ),
      script = """
        ins 27 "// end\n"
      """,
    )
  }

  @Test
  fun `a delete in the middle becomes one op`() {
    val base = document(
      """
      fun main() {
        println()
      }
      """
    )
    assertDiff(
      base = base,
      ops = listOf(delete(base, "  println()")),
      version1 = document(
        """
        fun main() {

        }
        """
      ),
      script = """
        del 13 "  println()"
      """,
    )
  }

  @Test
  fun `a replacement inside one line stays inside that line`() {
    val base = document(
      """
      fun main() {
        println(1)
      }
      """
    )
    assertDiff(
      base = base,
      // A replacement is a delete and then an insert at the same offset.
      ops = listOf(delete(base, "1"), insertBefore(base, "1", "42")),
      version1 = document(
        """
        fun main() {
          println(42)
        }
        """
      ),
      // The delete runs first, so the insert lands at the same offset.
      script = """
        del 23 "1"
        ins 23 "42"
      """,
    )
  }

  @Test
  fun `a whole new line becomes one insert`() {
    val base = document(
      """
      one
      two
      three
      """
    )
    assertDiff(
      base = base,
      ops = listOf(insertBefore(base, "three", "two and a half\n")),
      version1 = document(
        """
        one
        two
        two and a half
        three
        """
      ),
      // The op inserts the whole new line. A character diff alone would insert "wo and a half\nt"
      // at offset 9, which gives the same text and a worse anchor.
      script = """
        ins 8 "two and a half\n"
      """,
    )
  }

  @Test
  fun `several scattered edits round trip`() {
    val base = document(
      """
      alpha
      bravo
      charlie
      delta
      echo
      foxtrot
      """
    )
    assertDiff(
      base = base,
      // The ops run from the last offset to the first, so every offset indexes the base.
      ops = listOf(
        insertBefore(base, "foxtrot", "the "),
        delete(base, "delta\n"),
        insertBefore(base, "bravo", "BRAVO "),
      ),
      version1 = document(
        """
        alpha
        BRAVO bravo
        charlie
        echo
        the foxtrot
        """
      ),
      script = """
        ins 31 "the "
        del 20 "delta\n"
        ins 6 "BRAVO "
      """,
    )
  }

  @Test
  fun `an empty base becomes one insert`() {
    val version1 = document(
      """
      hello
      world
      """
    )
    assertDiff(
      base = "",
      ops = listOf(append("", version1)),
      version1 = version1,
      script = """
        ins 0 "hello\nworld\n"
      """,
    )
  }

  @Test
  fun `an empty target becomes one delete`() {
    val base = document(
      """
      hello
      world
      """
    )
    assertDiff(
      base = base,
      ops = listOf(deleteAll(base)),
      version1 = "",
      script = """
        del 0 "hello\nworld\n"
      """,
    )
  }

  @Test
  fun `an edit next to a surrogate pair keeps every pair whole`() {
    // "a<grinning face>b" changes to "a<beaming face>b". The two emoji share the high surrogate, so a
    // character diff can cut the pair in half.
    val base = DocumentText.createText("a😀b\n")
    val target = DocumentText.createText("a😁b\n")
    var text = base
    assertNoLoneSurrogate(text)
    for (op in DocTextDiff.diff(base, target, CancellationChecker.EMPTY)) {
      text = text.applyOp(op)
      assertNoLoneSurrogate(text)
    }
    assertSameText(target, text)
  }

  @Test
  fun `one huge line keeps the region small and every pair whole`() {
    // No line feed, so the growth to whole lines would cover the whole document. The limit stops it,
    // and the fallback still moves the boundary off the surrogate pair.
    val filler = "x".repeat(20_000)
    val base = DocumentText.createText("$filler😀$filler")
    val target = DocumentText.createText("$filler😁$filler")
    var text = base
    val recovered = DocTextDiff.diff(base, target, CancellationChecker.EMPTY)
    for (op in recovered) {
      text = text.applyOp(op)
      assertNoLoneSurrogate(text)
    }
    assertSameText(target, text)
    assertTrue(touchedChars(recovered) <= 4) { formatOps(base.string(), recovered) }
  }

  @Test
  fun `an edit before a shared low surrogate keeps every pair whole`() {
    // The two characters share the low surrogate and differ in the high one. So the trimmed suffix
    // ends inside the pair, and the fallback moves the end of the region off it.
    val filler = "x".repeat(20_000)
    val base = DocumentText.createText("$filler\uD83D\uDE00$filler")
    val target = DocumentText.createText("$filler\uD801\uDE00$filler")
    var text = base
    val recovered = DocTextDiff.diff(base, target, CancellationChecker.EMPTY)
    for (op in recovered) {
      text = text.applyOp(op)
      assertNoLoneSurrogate(text)
    }
    assertSameText(target, text)
    assertTrue(touchedChars(recovered) <= 4) { formatOps(base.string(), recovered) }
  }

  @Test
  fun `a random op sequence round trips`() {
    val random = Random(20260828)
    repeat(ROUNDS) { round ->
      val base = DocumentText.createText(randomText(random))
      val ops = randomOps(random, base)
      val version1 = applyOps(base, ops)
      val recovered = DocTextDiff.diff(base, version1, CancellationChecker.EMPTY)
      val version2 = applyOps(base, recovered)
      assertEquals(version1.string(), version2.string()) {
        "round $round\nbase: ${quote(base.string())}\nops: $ops\nscript:\n${formatOps(base.string(), recovered)}"
      }
      assertSameText(version1, version2)
      // The trivial script deletes the base and inserts the target. Never do worse than that.
      assertTrue(touchedChars(recovered) <= base.length() + version1.length()) {
        "round $round\nscript:\n${formatOps(base.string(), recovered)}"
      }
    }
  }

  @Test
  fun `local edits in a huge file stay local`() {
    val base = DocumentText.createText(Files.readString(hugeTextPath()))
    // The ops run from the last offset to the first, so every offset indexes the base.
    val ops = listOf(
      DocumentOp.insertOp(base.lineStartOffset(5000), "    myScrollingModel.dispose();\n"),
      DocumentOp.deleteOp(base.lineStartOffset(3000), base.lineEndOffset(3000) - base.lineStartOffset(3000)),
      DocumentOp.insertOp(base.lineStartOffset(120), "  // a note near the top\n"),
    )
    val version1 = applyOps(base, ops)
    val recovered = DocTextDiff.diff(base, version1, CancellationChecker.EMPTY)
    assertSameText(version1, applyOps(base, recovered))
    val touched = touchedChars(recovered)
    println("the source text: ${base.length()} chars; recovered ${recovered.size} ops over $touched chars")
    println(formatOps(base.string(), recovered))
    // A whole text replacement also round trips. It must not win here.
    assertTrue(recovered.size <= 2 * ops.size) { formatOps(base.string(), recovered) }
    assertTrue(touched < base.length() / 50) { "touched $touched of ${base.length()} chars" }
  }

  @Test
  fun `a cancelled caller stops the line comparison`() {
    // A whole new line needs no character comparison, so only the line comparison can ask.
    val base = DocumentText.createText(document("one\nthree"))
    val target = DocumentText.createText(document("one\ntwo\nthree"))
    val indicator = CancelAfter(answers = 0)
    assertThrows(Cancelled::class.java) {
      DocTextDiff.diff(base, target, indicator)
    }
  }

  @Test
  fun `a cancelled caller stops the character comparison`() {
    // The line comparison asks once, so the second question comes from the character comparison.
    val base = DocumentText.createText(document("val first = 1"))
    val target = DocumentText.createText(document("val second = 1"))
    val indicator = CancelAfter(answers = 1)
    assertThrows(Cancelled::class.java) {
      DocTextDiff.diff(base, target, indicator)
    }
  }

  /**
   * Runs the use case and checks every step.
   *
   * [base] changes to [version1] through [ops]. The test states [version1] in full, so the reader
   * sees the diff without counting an offset. [DocTextDiff] then reads only the two texts and returns
   * its own ops. Those ops must read as [script]. The version 2 applies them to [base], and it must
   * hold the same text and the same line structure as the version 1.
   *
   * [script] is a text block. The helper trims its indent. Use `""` for an empty script.
   */
  private fun assertDiff(
      base: String,
      ops: List<DocumentOp.Text>,
      version1: String,
      script: String,
  ) {
    val baseText = DocumentText.createText(base)
    val expected = applyOps(baseText, ops)
    assertEquals(version1, expected.string()) { "the ops do not build the stated version 1" }

    val recovered = DocTextDiff.diff(baseText, expected, CancellationChecker.EMPTY)
    assertEquals(script.trimIndent(), formatOps(base, recovered)) { "the recovered script changed" }

    val actual = applyOps(baseText, recovered)
    assertEquals(version1, actual.string()) { "the script does not rebuild the version 1" }
    assertSameText(expected, actual)
  }

  private fun applyOps(base: DocumentText, ops: List<DocumentOp.Text>): DocumentText {
    var text = base
    for (op in ops) {
      text = text.applyOp(op)
    }
    return text
  }

  /**
   * The number of characters that [ops] insert or delete. A small number means a small event graph.
   */
  private fun touchedChars(ops: List<DocumentOp.Text>): Int {
    var count = 0
    for (op in ops) {
      count += when (op) {
        is DocumentOp.Insert -> op.fragment().length
        is DocumentOp.Delete -> op.length()
      }
    }
    return count
  }

  private fun assertNoLoneSurrogate(text: DocumentText) {
    val chars = text.chars()
    var offset = 0
    while (offset < chars.length) {
      val char = chars[offset]
      if (Character.isHighSurrogate(char)) {
        val paired = offset + 1 < chars.length && Character.isLowSurrogate(chars[offset + 1])
        assertTrue(paired) { "a lone high surrogate at $offset of ${quote(chars.toString())}" }
        offset += 2
        continue
      }
      assertTrue(!Character.isLowSurrogate(char)) { "a lone low surrogate at $offset of ${quote(chars.toString())}" }
      offset++
    }
  }

  private fun randomText(random: Random): String {
    val text = StringBuilder()
    repeat(random.nextInt(120)) {
      text.append(ALPHABET[random.nextInt(ALPHABET.length)])
    }
    return text.toString()
  }

  private fun randomOps(random: Random, base: DocumentText): List<DocumentOp.Text> {
    val ops = ArrayList<DocumentOp.Text>()
    var text = base
    repeat(1 + random.nextInt(8)) {
      val op = randomOp(random, text.length())
      ops.add(op)
      text = text.applyOp(op)
    }
    return ops
  }

  private fun randomOp(random: Random, length: Int): DocumentOp.Text {
    if (length == 0 || random.nextBoolean()) {
      val offset = random.nextInt(length + 1)
      val fragment = StringBuilder()
      repeat(1 + random.nextInt(8)) {
        fragment.append(ALPHABET[random.nextInt(ALPHABET.length)])
      }
      return DocumentOp.insertOp(offset, fragment.toString())
    }
    val offset = random.nextInt(length)
    val opLength = 1 + random.nextInt(minOf(8, length - offset))
    return DocumentOp.deleteOp(offset, opLength)
  }

  /**
   * A checker that lets [answers] questions pass, and cancels at the next one.
   */
  private class CancelAfter(private val answers: Int) : CancellationChecker {
    private var asked = 0

    override fun checkCanceled() {
      asked++
      if (asked > answers) {
        throw Cancelled()
      }
    }
  }

  /**
   * The exception of [CancelAfter]. Its own type proves that the checker threw it.
   */
  private class Cancelled : RuntimeException()

  private fun hugeTextPath(): Path {
    val testData = Path.of(PathManager.getCommunityHomePath(), "platform/platform-tests/testData")
    return testData.resolve("editor/docBranch/EditorImpl.java.txt")
  }

  companion object {
    private const val ROUNDS = 2000
    private const val ALPHABET = "abcdef \n\r"
  }
}

/**
 * [ops] as one line each, in the order the ops run.
 *
 * Every offset indexes the base, because a script runs from the last changed region to the first.
 * So a delete can show the text that it removes.
 */
internal fun formatOps(base: String, ops: List<DocumentOp.Text>): String {
  return ops.joinToString("\n") { op ->
    when (op) {
      is DocumentOp.Insert -> "ins ${op.offset()} ${quote(op.fragment().toString())}"
      is DocumentOp.Delete -> "del ${op.offset()} ${quote(base.substring(op.offset(), op.offset() + op.length()))}"
    }
  }
}

/**
 * [text] on one line, in quotation marks, with every line break and quotation mark escaped.
 */
internal fun quote(text: String): String {
  val escaped = text
    .replace("\\", "\\\\")
    .replace("\"", "\\\"")
    .replace("\n", "\\n")
    .replace("\r", "\\r")
  return "\"$escaped\""
}

/**
 * The document that a text block states. The block drops the trailing line break, so this adds it.
 */
internal fun document(block: String): String = block.trimIndent() + "\n"

/**
 * An insert of [fragment] right before the first [anchor] of [base].
 */
internal fun insertBefore(base: String, anchor: String, fragment: String): DocumentOp.Text {
  return DocumentOp.insertOp(offsetOf(base, anchor), fragment)
}

/**
 * An insert of [fragment] at the end of [base].
 */
internal fun append(base: String, fragment: String): DocumentOp.Text = DocumentOp.insertOp(base.length, fragment)

/**
 * A delete of the first [fragment] of [base].
 */
internal fun delete(base: String, fragment: String): DocumentOp.Text {
  return DocumentOp.deleteOp(offsetOf(base, fragment), fragment.length)
}

/**
 * A delete of the whole [base].
 */
internal fun deleteAll(base: String): DocumentOp.Text = DocumentOp.deleteOp(0, base.length)

private fun offsetOf(base: String, fragment: String): Int {
  val offset = base.indexOf(fragment)
  require(offset >= 0) { "${quote(fragment)} is not in the base text" }
  return offset
}
