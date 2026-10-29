// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.settings.v2.docs

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.createParentDirectories
import kotlin.io.path.writeText

/**
 * Runs [DocLinkChecker], [AdrChainChecker], [AdrNumberChecker] and [ProposedAdrChecker] on small feature folders, so
 * each kind of problem is found and each correct folder passes.
 */
class DocCheckersTest {
  @TempDir
  lateinit var repository: Path

  private val feature: Path get() = repository.resolve("feature")

  private fun write(relativePath: String, text: String) {
    feature.resolve(relativePath).createParentDirectories().writeText(text.trimIndent() + "\n")
  }

  private fun linkProblems(
    checkedRoot: Path = repository,
    excludedSources: List<Path> = emptyList(),
    extraMarkdown: List<Path> = emptyList(),
  ): List<String> =
    DocLinkChecker(feature, checkedRoot, listOf(feature.resolve("src")), excludedSources, extraMarkdown)
      .findProblems().map { it.message }

  private fun chainProblems(maxChainLength: Int = 2): List<String> =
    AdrChainChecker(feature.resolve("adr"), maxChainLength).findProblems().map { it.message }

  private fun numberProblems(): List<String> =
    AdrNumberChecker(feature.resolve("adr")).findProblems().map { it.message }

  private fun proposedProblems(): List<String> =
    ProposedAdrChecker(feature.resolve("adr")).findProblems().map { it.message }

  private fun adr(number: String, status: String, slug: String = "decision-$number", earlierDecisions: String = "") {
    write("adr/$number-$slug.md", """
      |# $number. Decision $number
      |
      |## Status
      |
      |$status
      |
      |## Context
      |
      |Text.
      |$earlierDecisions
    """.trimMargin())
  }

  private fun foldEntries(vararg numbers: String): String =
    "\n## Earlier decisions\n" + numbers.joinToString("") { "\n### $it, 2026-01-01: the decision $it\n\nText.\n" }

  @Test
  fun `correct links pass`() {
    adr("0001", "Accepted.")
    write("spec/a.spec.md", """
      ---
      name: A
      targets:
        - ../src/A.kt
      ---

      # A

      - A rule. See [ADR 0001](../adr/0001-decision-0001.md) and [B](b.spec.md#section).
        [@test] ../src/A.kt
      - An [external link](https://example.com).
    """)
    write("spec/b.spec.md", "# B")
    write("src/A.kt", """
      // @spec ../spec/a.spec.md
      /** Follows ADR 0001. */
      class A
    """)
    assertEquals(emptyList<String>(), linkProblems())
  }

  @Test
  fun `a broken link, target, test path and backlink are found`() {
    write("spec/a.spec.md", """
      ---
      targets:
        - ../src/Missing.kt
      ---

      See [B](missing.spec.md).
      [@test] ../src/MissingTest.kt
    """)
    write("src/A.kt", "// @spec ../spec/missing.spec.md")
    assertEquals(listOf(
      "the path ../src/Missing.kt does not exist",
      "the link target missing.spec.md does not exist",
      "the path ../src/MissingTest.kt does not exist",
      "the backlink ../spec/missing.spec.md does not exist",
    ), linkProblems())
  }

  @Test
  fun `a wrong ADR number, a plain mention and a missing ADR in a source are found`() {
    adr("0001", "Accepted.")
    adr("0002", "Accepted.")
    write("spec/a.spec.md", """
      See [ADR 0002](../adr/0001-decision-0001.md). As ADR 0001 says.
    """)
    write("src/A.kt", "/** Follows ADR 0003. */")
    assertEquals(listOf(
      "the link [ADR 0002] points to 0001-decision-0001.md, not to ADR 0002",
      "the mention ADR 0001 is not a link",
      "no ADR exists for the mention ADR 0003",
    ), linkProblems())
  }

  @Test
  fun `a link outside the checked root is skipped`() {
    repository.resolve("other").createDirectories()
    write("spec/a.spec.md", "See [the other part](../../other/missing.md).")
    write("src/A.kt", "// @spec ../../other/missing.spec.md")
    assertEquals(listOf("the link target ../../other/missing.md does not exist",
                        "the backlink ../../other/missing.spec.md does not exist"), linkProblems())
    assertEquals(emptyList<String>(), linkProblems(checkedRoot = feature))
  }

  @Test
  fun `a link in a fenced code block is not checked`() {
    write("spec/a.spec.md", """
      ```
      [x](missing.md) and ADR 0001
      ```
    """)
    assertEquals(emptyList<String>(), linkProblems())
  }

  @Test
  fun `a chain within the limit passes`() {
    adr("0001", "Accepted. Amended by [ADR 0002](./0002-decision-0002.md).")
    adr("0002", "Accepted. Amends [ADR 0001](./0001-decision-0001.md).")
    assertEquals(emptyList<String>(), chainProblems())
  }

  @Test
  fun `a chain longer than the limit is found`() {
    adr("0001", "Superseded by [ADR 0002](./0002-decision-0002.md).")
    adr("0002", "Accepted. Supersedes [ADR 0001](./0001-decision-0001.md). Amended by [ADR 0003](./0003-decision-0003.md).")
    adr("0003", "Accepted. Amends [ADR 0002](./0002-decision-0002.md).")
    assertEquals(listOf("the chain 0001 -> 0002 -> 0003 holds 3 ADRs, more than 2. Merge these ADRs into one decision"),
                 chainProblems())
    assertEquals(emptyList<String>(), chainProblems(maxChainLength = 3))
  }

  @Test
  fun `a relation that the other ADR does not name back is found`() {
    adr("0001", "Accepted.")
    adr("0002", "Accepted. Amends [ADR 0001](./0001-decision-0001.md).")
    adr("0003", "Accepted. Amended by [ADR 0004](./0004-decision-0004.md).")
    adr("0004", "Accepted.")
    assertEquals(listOf("Amends ADR 0001, but ADR 0001 has no Amended by ADR 0002",
                        "Amended by ADR 0004, but ADR 0004 has no Amends ADR 0003"), chainProblems())
  }

  @Test
  fun `a proposed ADR may amend an ADR that does not name it yet`() {
    adr("0001", "Accepted.")
    adr("0002", "Proposed. Amends [ADR 0001](./0001-decision-0001.md).")
    assertEquals(emptyList<String>(), chainProblems())
  }

  @Test
  fun `a relation to a missing ADR is found`() {
    adr("0001", "Accepted. Amends [ADR 0009](./0009-missing.md).")
    assertEquals(listOf("Amends names ADR 0009, which does not exist"), chainProblems())
  }

  @Test
  fun `a cycle is found`() {
    // Each ADR names both relations to the other, so every relation is two-sided and only the cycle is reported.
    adr("0001", "Accepted. Amended by [ADR 0002](./0002-decision-0002.md). Amends [ADR 0002](./0002-decision-0002.md).")
    adr("0002", "Accepted. Amends [ADR 0001](./0001-decision-0001.md). Amended by [ADR 0001](./0001-decision-0001.md).")
    assertEquals(listOf("the ADRs form a cycle: 0001 -> 0002 -> 0001"), chainProblems())
  }

  @Test
  fun `a one-sided supersession is found`() {
    adr("0001", "Superseded by [ADR 0002](./0002-decision-0002.md).")
    adr("0002", "Accepted.")
    assertEquals(listOf("Superseded by ADR 0002, but ADR 0002 has no Supersedes ADR 0001"), chainProblems())
  }

  @Test
  fun `a link after the sentence of a relation is not a relation`() {
    adr("0001", "Accepted ([ISSUE-1](https://example.com/ISSUE-1)). Amended by [ADR 0002](./0002-decision-0002.md).")
    adr("0002", "Accepted. Amends [ADR 0001](./0001-decision-0001.md). See also [ADR 0003](./0003-decision-0003.md).")
    adr("0003", "Accepted.")
    assertEquals(emptyList<String>(), chainProblems())
  }

  @Test
  fun `a wrapped link text, an indented fence and inline code are handled`() {
    adr("0001", "Accepted.")
    write("spec/a.spec.md", """
      - See [the first
        ADR 0001](../adr/0001-decision-0001.md).
      - An example:
        ```
        [x](missing.md) and ADR 0001
        ```
      - The form is `[@test] ../missing.kt`, and `ADR 0001` names a decision.
    """)
    assertEquals(emptyList<String>(), linkProblems())
  }

  @Test
  fun `a glob target must match a file, and a quoted target is unquoted`() {
    write("src/a/A.kt", "class A")
    write("spec/a.spec.md", """
      ---
      targets:
        - ../src/**/*.kt
        - "../src/a/A.kt"
        - ../src/**/*.java
      ---
    """)
    assertEquals(listOf("the path ../src/**/*.java does not exist"), linkProblems())
  }

  @Test
  fun `an excluded source is not checked`() {
    write("src/A.kt", "// @spec ../spec/missing.spec.md")
    assertEquals(emptyList<String>(), linkProblems(excludedSources = listOf(feature.resolve("src/A.kt"))))
  }

  @Test
  fun `an extra Markdown file may hold a plain mention, but its links are checked`() {
    adr("0001", "Accepted.")
    adr("0002", "Accepted.")
    write("AGENTS.md", "Cite a decision as ADR 0001. See [ADR 0002](adr/0001-decision-0001.md).")
    assertEquals(listOf("the link [ADR 0002] points to 0001-decision-0001.md, not to ADR 0002"),
                 linkProblems(extraMarkdown = listOf(feature.resolve("AGENTS.md"))))
  }

  @Test
  fun `numbers that each belong to one file or one fold entry pass`() {
    adr("0001", "Accepted.")
    adr("0004", "Accepted.", earlierDecisions = foldEntries("0002", "0003"))
    assertEquals(emptyList<String>(), numberProblems())
  }

  @Test
  fun `two files with the same number are found`() {
    adr("0001", "Accepted.", slug = "first-branch")
    adr("0001", "Accepted.", slug = "second-branch")
    assertEquals(listOf("the number 0001 is used 2 times: the file 0001-first-branch.md, the file 0001-second-branch.md"),
                 numberProblems())
  }

  @Test
  fun `a file that reuses a folded number is found`() {
    adr("0001", "Accepted.")
    adr("0002", "Accepted.", earlierDecisions = foldEntries("0001"))
    assertEquals(listOf("the number 0001 is used 2 times: the file 0001-decision-0001.md, " +
                        "the fold entry in 0002-decision-0002.md:13"), numberProblems())
  }

  @Test
  fun `two merged ADRs that fold the same number are found`() {
    adr("0002", "Accepted.", earlierDecisions = foldEntries("0001"))
    adr("0003", "Accepted.", earlierDecisions = foldEntries("0001"))
    assertEquals(listOf("the number 0001 is used 2 times: the fold entry in 0002-decision-0002.md:13, " +
                        "the fold entry in 0003-decision-0003.md:13"), numberProblems())
  }

  @Test
  fun `a number that no file and no fold entry uses is found`() {
    adr("0001", "Accepted.")
    adr("0004", "Accepted.", earlierDecisions = foldEntries("0003"))
    assertEquals(listOf("no ADR file and no fold entry uses the number 0002"), numberProblems())
  }

  @Test
  fun `a second merge must keep the fold entries of the merged ADR it folds`() {
    adr("0003", "Accepted.", earlierDecisions = foldEntries("0001", "0002"))
    assertEquals(emptyList<String>(), numberProblems())
    adr("0003", "Accepted.", earlierDecisions = foldEntries("0002"))
    assertEquals(listOf("no ADR file and no fold entry uses the number 0001"), numberProblems())
  }

  @Test
  fun `a numbered heading outside the earlier decisions is not a fold entry`() {
    adr("0001", "Accepted.")
    adr("0003", "Accepted.", earlierDecisions = """
      |
      |## Rejected alternatives
      |
      |### 0001, the old way
      |
      |## Earlier decisions
      |
      |### 0002, 2026-01-01: the decision 0002
      |
      |## Notes
      |
      |### 0001, a later section
    """.trimMargin())
    assertEquals(emptyList<String>(), numberProblems())
  }

  @Test
  fun `a numbered heading in a code block of the earlier decisions is not a fold entry`() {
    adr("0001", "Accepted.")
    adr("0003", "Accepted.", earlierDecisions = """
      |
      |## Earlier decisions
      |
      |```markdown
      |## Earlier decisions
      |
      |### 0001, 2026-01-01: an example entry
      |```
      |
      |### 0002, 2026-01-01: the decision 0002
    """.trimMargin())
    assertEquals(emptyList<String>(), numberProblems())
  }

  @Test
  fun `an empty or missing ADR folder has no number problems`() {
    assertEquals(emptyList<String>(), numberProblems())
    feature.resolve("adr").createDirectories()
    assertEquals(emptyList<String>(), numberProblems())
  }

  @Test
  fun `accepted ADRs pass the Proposed check`() {
    adr("0001", "Accepted. It was Proposed first.")
    adr("0002", "Accepted ([ISSUE-1](https://example.com/ISSUE-1)).")
    assertEquals(emptyList<String>(), proposedProblems())
  }

  @Test
  fun `a Proposed ADR is found`() {
    adr("0001", "Accepted.")
    adr("0002", "Proposed ([ISSUE-1](https://example.com/ISSUE-1)).")
    assertEquals(listOf("ADR 0002 is Proposed. Accept it before it goes to master"), proposedProblems())
  }

  @Test
  fun `a file with Windows line separators is read`() {
    adr("0001", "Accepted.")
    feature.resolve("adr/0002-decision-0002.md").writeText("# 0002\r\n\r\n## Status\r\n\r\nAccepted. Amends [ADR 0001](./0001-decision-0001.md).\r\n")
    feature.resolve("spec").createDirectories()
    feature.resolve("spec/a.spec.md").writeText("---\r\ntargets:\r\n  - ../src/Missing.kt\r\n---\r\n")
    assertEquals(listOf("Amends ADR 0001, but ADR 0001 has no Amended by ADR 0002"), chainProblems())
    assertEquals(listOf("the path ../src/Missing.kt does not exist"), linkProblems())
  }
}
