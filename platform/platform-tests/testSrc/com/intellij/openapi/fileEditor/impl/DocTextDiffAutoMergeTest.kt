// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.fileEditor.impl

import com.intellij.diff.comparison.CancellationChecker
import com.intellij.openapi.editor.ex.DocumentOp
import com.intellij.openapi.editor.ex.DocumentText
import com.intellij.openapi.editor.ex.experimental.DocumentBranch
import com.intellij.openapi.editor.ex.experimental.agent
import com.intellij.openapi.editor.ex.experimental.string
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * Tests the auto merge of a disk change into a document that holds unsaved edits.
 *
 * The case is one an agent creates. The user opens a file and types. An agent rewrites the same file
 * on disk. Neither side knows about the other, and the disk holds only a text, not a history. So the
 * IDE has to recover a history for the disk change and merge it.
 *
 * Every test runs the same seven steps, and [assertAutoMerge] checks each one.
 * 1. A saved [DocumentBranch] holds the text that the disk also holds.
 * 2. The user forks it and types. Those edits reach no file.
 * 3. An agent writes a new text to the file.
 * 4. [DocTextDiff] reads the saved text and the disk text, and returns the ops.
 * 5. A second fork applies those ops, so the disk change becomes a branch with a history.
 * 6. The two branches merge.
 * 7. The merged text holds both sides.
 *
 * Each test states the four texts in full, so the reader sees the saved document, sees what each
 * side did to it, and sees what the merge produced. The ops that step 4 recovers stay out of sight
 * on purpose. [DocTextDiffTest] pins those, and repeating them here would tie a merge test to the
 * offsets that the diff happens to choose.
 *
 * Two rules of the merge decide the outcome, and both are visible below.
 * - The merge drops no edit. A user keystroke survives even when the agent deleted the text around it.
 * - Two inserts at one position order by the agent name. The agent branch is named "disk" and the
 *   user branch is named "user", so the disk text comes first.
 */
class DocTextDiffAutoMergeTest {

  /**
   * The saved document. Every test starts from this text, and the disk holds it too.
   */
  private fun savedDocument(): String = document(
    """
    package demo

    fun main() {
      val name = "world"
      println(name)
    }
    """
  )

  @Test
  fun `the agent and the user change different parts`() {
    val saved = savedDocument()
    assertAutoMerge(
      saved = saved,
      // The user renames the greeting.
      userOps = listOf(delete(saved, "world"), insertBefore(saved, "world", "there")),
      userText = document(
        """
        package demo

        fun main() {
          val name = "there"
          println(name)
        }
        """
      ),
      // The agent appends a function.
      diskText = document(
        """
        package demo

        fun main() {
          val name = "world"
          println(name)
        }

        fun greet() {
          println("hi")
        }
        """
      ),
      // Both changes survive, and neither disturbs the other.
      merged = document(
        """
        package demo

        fun main() {
          val name = "there"
          println(name)
        }

        fun greet() {
          println("hi")
        }
        """
      ),
    )
  }

  @Test
  fun `the agent adds an import while the user edits the body`() {
    val saved = savedDocument()
    assertAutoMerge(
      saved = saved,
      userOps = listOf(insertBefore(saved, "}", "  println(name.length)\n")),
      userText = document(
        """
        package demo

        fun main() {
          val name = "world"
          println(name)
          println(name.length)
        }
        """
      ),
      diskText = document(
        """
        package demo

        import demo.util

        fun main() {
          val name = "world"
          println(name)
        }
        """
      ),
      merged = document(
        """
        package demo

        import demo.util

        fun main() {
          val name = "world"
          println(name)
          println(name.length)
        }
        """
      ),
    )
  }

  @Test
  fun `both add a line at the same place`() {
    val saved = savedDocument()
    assertAutoMerge(
      saved = saved,
      userOps = listOf(insertBefore(saved, "  println(name)", "  // TODO: check the name\n")),
      userText = document(
        """
        package demo

        fun main() {
          val name = "world"
          // TODO: check the name
          println(name)
        }
        """
      ),
      diskText = document(
        """
        package demo

        fun main() {
          val name = "world"
          require(name.isNotEmpty())
          println(name)
        }
        """
      ),
      // Both lines land. The agent name breaks the tie, so "disk" comes before "user".
      merged = document(
        """
        package demo

        fun main() {
          val name = "world"
          require(name.isNotEmpty())
          // TODO: check the name
          println(name)
        }
        """
      ),
    )
  }

  @Test
  fun `both append a function at the end`() {
    val saved = savedDocument()
    assertAutoMerge(
      saved = saved,
      userOps = listOf(append(saved, "\nfun helper() {\n}\n")),
      userText = document(
        """
        package demo

        fun main() {
          val name = "world"
          println(name)
        }

        fun helper() {
        }
        """
      ),
      diskText = document(
        """
        package demo

        fun main() {
          val name = "world"
          println(name)
        }

        fun generated() {
        }
        """
      ),
      merged = document(
        """
        package demo

        fun main() {
          val name = "world"
          println(name)
        }

        fun generated() {
        }

        fun helper() {
        }
        """
      ),
    )
  }

  @Test
  fun `the agent deletes a line that the user was editing`() {
    val saved = savedDocument()
    assertAutoMerge(
      saved = saved,
      // The user is in the middle of typing ".length" when the agent deletes the whole line.
      userOps = listOf(insertBefore(saved, ")\n", ".length")),
      userText = document(
        """
        package demo

        fun main() {
          val name = "world"
          println(name.length)
        }
        """
      ),
      diskText = document(
        """
        package demo

        fun main() {
          val name = "world"
        }
        """
      ),
      // The merge drops no edit, so the typed characters outlive the line that held them. The text is
      // broken, and that is the honest price of never losing what the user typed.
      merged = document(
        """
        package demo

        fun main() {
          val name = "world"
        .length}
        """
      ),
    )
  }

  @Test
  fun `the agent reformats the file while the user adds a line`() {
    val saved = savedDocument()
    assertAutoMerge(
      saved = saved,
      userOps = listOf(insertBefore(saved, "fun main", "// entry point\n")),
      userText = document(
        """
        package demo

        // entry point
        fun main() {
          val name = "world"
          println(name)
        }
        """
      ),
      diskText = document(
        """
        package demo

        fun main() {
            val name = "world"
            println(name)
        }
        """
      ),
      merged = document(
        """
        package demo

        // entry point
        fun main() {
            val name = "world"
            println(name)
        }
        """
      ),
    )
  }

  /**
   * Runs the seven steps and checks each one.
   *
   * [saved] is the text that the document and the file both hold. [userOps] are the unsaved edits, and
   * they must build [userText]. [diskText] is what the agent wrote, and the recovered ops must rebuild
   * it. The merge of the two branches must give [merged], in either order.
   */
  private fun assertAutoMerge(
    saved: String,
    userOps: List<DocumentOp.Text>,
    userText: String,
    diskText: String,
    merged: String,
  ) {
    // 1. The saved document. The file on disk holds the same text.
    val savedBranch = DocumentBranch.createBranch(saved, agent("saved"))

    // 2. The user types in the open document. Nothing reaches the file.
    var userBranch = savedBranch.fork(agent("user"))
    for (op in userOps) {
      userBranch = userBranch.applyOp(op)
    }
    assertEquals(userText, userBranch.string()) { "the ops do not build the stated user text" }

    // 3, 4. The agent left only a text behind, so recover a script for it.
    val disk = DocumentText.createText(diskText)
    val ops = DocTextDiff.diff(savedBranch.text(), disk, CancellationChecker.EMPTY)

    // 5. The script becomes a branch, under an agent of its own.
    var diskBranch = savedBranch.fork(agent("disk"))
    for (op in ops) {
      diskBranch = diskBranch.applyOp(op)
    }
    assertEquals(diskText, diskBranch.string()) { "the script does not rebuild the disk text" }

    // 6, 7. The merge keeps both sides, and the order of the merge changes nothing.
    assertEquals(merged, userBranch.merge(diskBranch).string()) { "the user side merged first" }
    assertEquals(merged, diskBranch.merge(userBranch).string()) { "the disk side merged first" }
  }
}
