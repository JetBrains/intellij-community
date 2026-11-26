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
 * Runs [DocLinkChecker], [AdrChainChecker], [AdrIdChecker] and [ProposedAdrChecker] on small feature folders, so
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

  private fun idProblems(lastAdrNumber: Int = 99): List<String> {
    val checker = AdrIdChecker(feature.resolve("adr"), lastAdrNumber)
    val problems = checker.shapeProblems() + checker.newNumberProblems() + checker.duplicateProblems() + checker.recordProblems()
    return problems.map { it.message }
  }

  private fun proposedProblems(): List<String> =
    ProposedAdrChecker(feature.resolve("adr")).findProblems().map { it.message }

  private fun adr(
    id: String,
    status: String,
    slug: String = "decision-$id",
    earlierDecisions: String = "",
    date: String? = null,
  ) {
    val dateLine = date?.let { "\n\nDate: $it" }.orEmpty()
    write("adr/$id-$slug.md", """
      |# $id. Decision $id$dateLine
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

  private fun foldEntries(vararg ids: String): String =
    "\n## Earlier decisions\n" + ids.joinToString("") { "\n### $it, 2026-01-01: the decision $it\n\nText.\n" }

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
  fun `correct links to a six-character ADR pass`() {
    adr("00001C", "Accepted.")
    write("AGENTS.md", "# Rules")
    write("spec/a.spec.md", """
      See [ADR 00001C](../adr/00001C-decision-00001C.md) and [AGENTS](../AGENTS.md).
    """)
    write("src/A.kt", "/** Follows ADR 00001C. */")
    assertEquals(emptyList<String>(), linkProblems())
  }

  @Test
  fun `a wrong link and a plain mention of a six-character ADR are found`() {
    adr("0001", "Accepted.")
    adr("00001C", "Accepted.")
    write("spec/a.spec.md", """
      See [ADR 00002A](../adr/00001C-decision-00001C.md).
      See [ADR 000001](../adr/0001-decision-0001.md).
      As ADR 00001C says.
    """)
    write("src/A.kt", "/** Follows ADR 00002A. */")
    assertEquals(listOf(
      "the link [ADR 00002A] points to 00001C-decision-00001C.md, not to ADR 00002A",
      "the link [ADR 000001] points to 0001-decision-0001.md, not to ADR 000001",
      "the mention ADR 00001C is not a link",
      "no ADR exists for the mention ADR 00002A",
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
  fun `a chain that mixes both id shapes is found`() {
    adr("0001", "Superseded by [ADR 00001C](./00001C-decision-00001C.md).")
    adr("00001C", "Accepted. Supersedes [ADR 0001](./0001-decision-0001.md). Amended by [ADR 00002A](./00002A-decision-00002A.md).")
    adr("00002A", "Accepted. Amends [ADR 00001C](./00001C-decision-00001C.md).")
    assertEquals(listOf("the chain 0001 -> 00001C -> 00002A holds 3 ADRs, more than 2. Merge these ADRs into one decision"),
                 chainProblems())
    assertEquals(emptyList<String>(), chainProblems(maxChainLength = 3))
  }

  @Test
  fun `the relation problems of both id shapes come in time order`() {
    adr("00001C", "Accepted. Amended by [ADR 00002A](./00002A-decision-00002A.md).")
    adr("00002A", "Accepted.")
    adr("0005", "Accepted. Amended by [ADR 0006](./0006-decision-0006.md).")
    adr("0006", "Accepted.")
    assertEquals(listOf("Amended by ADR 0006, but ADR 0006 has no Amends ADR 0005",
                        "Amended by ADR 00002A, but ADR 00002A has no Amends ADR 00001C"), chainProblems())
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
  fun `ids of both shapes that each belong to one file or one fold entry pass`() {
    adr("0001", "Accepted.")
    adr("0004", "Accepted.", earlierDecisions = foldEntries("0002"))
    adr("00002A", "Accepted.", date = "2026-11-18T05:21:47Z", earlierDecisions = foldEntries("0003", "00001C"))
    assertEquals(emptyList<String>(), idProblems())
  }

  @Test
  fun `a file name that does not start with an ADR id is found`() {
    write("adr/01abcd-lowercase.md", "# 01abcd. Decision")
    write("adr/42-short.md", "# 42. Decision")
    write("adr/README.md", "# ADRs")
    assertEquals(listOf(
      "the file name 01abcd-lowercase.md does not start with a four-digit number or a six-character id",
      "the file name 42-short.md does not start with a four-digit number or a six-character id",
      "the file name README.md does not start with a four-digit number or a six-character id",
    ), idProblems())
  }

  @Test
  fun `a number above the last number is found`() {
    adr("0098", "Accepted.", earlierDecisions = foldEntries("0101"))
    adr("0099", "Accepted.")
    adr("0100", "Accepted.")
    assertEquals(listOf(
      "the fold entry in 0098-decision-0098.md:13 has the number 0101, above the last number 0099. A new ADR takes a time-based id",
      "the file 0100-decision-0100.md has the number 0100, above the last number 0099. A new ADR takes a time-based id",
    ), idProblems(lastAdrNumber = 99))
  }

  @Test
  fun `two files with the same number are found`() {
    adr("0001", "Accepted.", slug = "first-branch")
    adr("0001", "Accepted.", slug = "second-branch")
    assertEquals(listOf("the id 000001 is used 2 times: the file 0001-first-branch.md, the file 0001-second-branch.md"),
                 idProblems())
  }

  @Test
  fun `two ADRs that two branches create in the same second are found`() {
    adr("00001C", "Accepted.", slug = "first-branch", date = "2026-11-18T05:21:13Z")
    adr("00001C", "Accepted.", slug = "second-branch", date = "2026-11-18T05:21:13Z")
    assertEquals(listOf("the id 00001C is used 2 times: the file 00001C-first-branch.md, the file 00001C-second-branch.md"),
                 idProblems())
  }

  @Test
  fun `a number and the six-character id with the same value are found`() {
    adr("0042", "Accepted.")
    adr("000042", "Accepted.", date = "2026-11-18T05:22:51Z")
    assertEquals(listOf("the id 000042 is used 2 times: the file 000042-decision-000042.md, the file 0042-decision-0042.md"),
                 idProblems())
  }

  @Test
  fun `a file that reuses a folded number is found`() {
    adr("0001", "Accepted.")
    adr("0002", "Accepted.", earlierDecisions = foldEntries("0001"))
    assertEquals(listOf("the id 000001 is used 2 times: the file 0001-decision-0001.md, " +
                        "the fold entry in 0002-decision-0002.md:13"), idProblems())
  }

  @Test
  fun `a file that reuses a folded six-character id is found`() {
    adr("00001C", "Accepted.", date = "2026-11-18T05:21:13Z")
    adr("00002A", "Accepted.", date = "2026-11-18T05:21:47Z", earlierDecisions = foldEntries("00001C"))
    assertEquals(listOf("the id 00001C is used 2 times: the file 00001C-decision-00001C.md, " +
                        "the fold entry in 00002A-decision-00002A.md:15"), idProblems())
  }

  @Test
  fun `a fold entry with a number and a file with the same six-character id are found`() {
    adr("0001", "Accepted.", earlierDecisions = foldEntries("0042"))
    adr("000042", "Accepted.", date = "2026-11-18T05:22:51Z")
    assertEquals(listOf("the id 000042 is used 2 times: the fold entry in 0001-decision-0001.md:13, " +
                        "the file 000042-decision-000042.md"), idProblems())
  }

  @Test
  fun `two merged ADRs that fold the same number are found`() {
    adr("0002", "Accepted.", earlierDecisions = foldEntries("0001"))
    adr("0003", "Accepted.", earlierDecisions = foldEntries("0001"))
    assertEquals(listOf("the id 000001 is used 2 times: the fold entry in 0002-decision-0002.md:13, " +
                        "the fold entry in 0003-decision-0003.md:13"), idProblems())
  }

  @Test
  fun `an id that does not match the date is found`() {
    adr("00001C", "Accepted.", date = "2026-11-18T05:21:14Z")
    adr("00002A", "Accepted.", date = "2026-11-18T05:20:24Z")
    assertEquals(listOf("the date 2026-11-18T05:21:14Z gives the id 00001D, not 00001C",
                        "the date 2026-11-18T05:20:24Z gives no id"), idProblems())
  }

  @Test
  fun `a title that does not start with the id is found`() {
    write("adr/00001C-decision.md", """
      # 00001D. Decision

      Date: 2026-11-18T05:21:13Z
    """)
    write("adr/00002A-decision.md", """
      # 00002A Decision

      Date: 2026-11-18T05:21:47Z
    """)
    assertEquals(listOf("the title does not start with `# 00001C. `, the id from the file name",
                        "the title does not start with `# 00002A. `, the id from the file name"), idProblems())
  }

  @Test
  fun `a file whose name starts with six capital letters and a dash is read as an ADR`() {
    write("adr/README-notes.md", "# Notes")
    assertEquals(listOf("the title does not start with `# README. `, the id from the file name",
                        "the Date line is not a UTC time in seconds, such as `Date: 2026-11-18T05:21:13Z`"), idProblems())
  }

  @Test
  fun `a six-character ADR without a date in seconds is found`() {
    adr("00001C", "Accepted.", date = "2026-11-18")
    adr("00002A", "Accepted.", date = "2026-11-18T05:21Z")
    adr("000042", "Accepted.")
    val problem = "the Date line is not a UTC time in seconds, such as `Date: 2026-11-18T05:21:13Z`"
    assertEquals(listOf(problem, problem, problem), idProblems())
  }

  @Test
  fun `the date of a merged six-character ADR is read before its merge date`() {
    adr("00001C", "Accepted.", date = "2026-11-18T05:21:13Z. Merged: 2026-12-01.")
    assertEquals(emptyList<String>(), idProblems())
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
    assertEquals(emptyList<String>(), idProblems())
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
    assertEquals(emptyList<String>(), idProblems())
  }

  @Test
  fun `an empty or missing ADR folder has no id problems`() {
    assertEquals(emptyList<String>(), idProblems())
    feature.resolve("adr").createDirectories()
    assertEquals(emptyList<String>(), idProblems())
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
    adr("00001C", "Proposed.")
    assertEquals(listOf("ADR 0002 is Proposed. Accept it before it goes to master",
                        "ADR 00001C is Proposed. Accept it before it goes to master"), proposedProblems())
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
