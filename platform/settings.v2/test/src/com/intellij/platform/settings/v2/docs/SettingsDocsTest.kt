// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.settings.v2.docs

import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.fixtures.IdeaTestExecutionPolicy
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path

/**
 * Checks the specs, the ADRs and the sources of settings v2 with [DocLinkChecker], [AdrChainChecker],
 * [AdrIdChecker] and [ProposedAdrChecker]. Each check of [AdrIdChecker] is its own test.
 *
 * The settings v2 modules outside `community/` live in `remote-dev/settings/v2`. A run from the community repository
 * has no such folder, and it skips the links that go there.
 */
class SettingsDocsTest {
  private val repositoryRoot = Path.of(IdeaTestExecutionPolicy.getHomePathWithPolicy()).normalize()
  private val featureRoot = Path.of(PlatformTestUtil.getCommunityPath(), "platform", "settings.v2").normalize()
  private val remoteDevRoot = repositoryRoot.resolve("remote-dev/settings/v2")
  private val adrDir = featureRoot.resolve(DocLinkChecker.ADR_DIR)
  private val adrIdChecker = AdrIdChecker(adrDir, LAST_ADR_NUMBER)

  @Test
  fun `every link between the specs, the ADRs and the sources resolves`() {
    val checkerTest = featureRoot.resolve("test/src/com/intellij/platform/settings/v2/docs/DocCheckersTest.kt")
    assertTrue(Files.exists(checkerTest)) { "the excluded source $checkerTest does not exist" }
    val checker = DocLinkChecker(
      featureRoot = featureRoot,
      checkedRoot = repositoryRoot,
      sourceRoots = listOf(featureRoot, remoteDevRoot),
      excludedSources = listOf(checkerTest),
      extraMarkdown = listOf(featureRoot.resolve(AGENTS_FILE), remoteDevRoot.resolve(AGENTS_FILE)),
    )
    assertNoProblems(checker.findProblems())
  }

  @Test
  fun `no ADR chain is longer than the limit`() {
    val checker = AdrChainChecker(adrDir, MAX_ADR_CHAIN_LENGTH)
    assertNoProblems(checker.findProblems())
  }

  @Test
  fun `each ADR file name starts with an ADR id`() {
    assertNoProblems(adrIdChecker.shapeProblems())
  }

  @Test
  fun `no ADR takes a number above the last number`() {
    assertNoProblems(adrIdChecker.newNumberProblems())
  }

  @Test
  fun `each ADR id belongs to one ADR file or one fold entry`() {
    assertNoProblems(adrIdChecker.duplicateProblems())
  }

  @Test
  fun `each time-based ADR id matches the title and the date of its ADR`() {
    assertNoProblems(adrIdChecker.recordProblems())
  }

  @Test
  fun `no ADR is Proposed`() {
    assertNoProblems(ProposedAdrChecker(adrDir).findProblems())
  }

  @Test
  fun `the ADR script counts the ids from the same base as the checks`() {
    val script = adrDir.resolve(NEW_ADR_SCRIPT)
    val scriptBase = SCRIPT_ID_BASE.find(Files.readString(script))?.groupValues?.get(1)?.toLong()
    assertEquals(ADR_ID_BASE.epochSecond, scriptBase) { "the line ADR_ID_BASE=<seconds> of $script" }
  }

  private fun assertNoProblems(problems: List<DocProblem>) {
    assertTrue(problems.isEmpty()) { "${problems.size} problems:\n" + problems.joinToString("\n") }
  }

  private companion object {
    /** Holds the largest number of ADRs that one amendment or supersession chain may hold. The feature `AGENTS.md` sets it. */
    const val MAX_ADR_CHAIN_LENGTH = 2

    /** Holds the highest ADR number at the move to the time-based ids. A newer ADR takes a time-based id. */
    const val LAST_ADR_NUMBER = 183

    /** Holds the name of the agent instructions file of a module. */
    const val AGENTS_FILE = "AGENTS.md"

    /** Holds the name of the script in the ADR folder that creates an ADR with a time-based id. */
    const val NEW_ADR_SCRIPT = "new-adr.sh"

    /** Matches the base of the ids in [NEW_ADR_SCRIPT], in epoch seconds. */
    val SCRIPT_ID_BASE = Regex("""^ADR_ID_BASE=(\d+)$""", RegexOption.MULTILINE)
  }
}
