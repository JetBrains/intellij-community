// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.changes

import com.intellij.diff.util.Side
import com.intellij.openapi.diff.impl.patch.PatchHunk
import com.intellij.openapi.diff.impl.patch.PatchLine
import com.intellij.openapi.diff.impl.patch.TextFilePatch
import com.intellij.openapi.vcs.FileStatus
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource

class GitTextFilePatchWithHistoryTest {
  @ParameterizedTest
  @MethodSource("testCases")
  fun `forceful mapping works`(case: TestCase) {
    val patch = createTestPatch(case)
    assertThat(patch.forcefullyMapLine(case.fromPatch, case.line, case.toSide))
      .isEqualTo(case.expected)
  }

  @ParameterizedTest
  @MethodSource("testCases")
  fun `regular mapping works`(case: TestCase) {
    val patch = createTestPatch(case)
    assertThat(patch.mapLine(case.fromPatch, case.line, case.toSide))
      .isEqualTo(if (case.isEstimate) case.toSide.other() to case.line // no mapping, just use the easy side TODO: fix with complex start-in-the-middle tests
                 else case.toSide to case.expected)
  }

  private fun createTestPatch(case : TestCase) = GitTextFilePatchWithHistory(
    TextFilePatch(Charsets.UTF_8, "\n").apply {
      beforeVersionId = ROOT_COMMIT
      afterVersionId = case.patches.last().name

      beforeName = if (case.patches.first().fileStatus == FileStatus.ADDED) null else FILE_NAME
      afterName = if (case.patches.last().fileStatus == FileStatus.DELETED) null else FILE_NAME
    },
    isCumulative = true,
    fileHistory = object : GitFileHistory {
      override fun findStartCommit(): String = ROOT_COMMIT
      override fun findFirstParent(commitSha: String): String? = case.patches.zipWithNext().find { it.second.name == commitSha }?.first?.name
      override fun contains(commitSha: String, filePath: String): Boolean = commitSha == ROOT_COMMIT || case.patches.any { it.name == commitSha }
      override fun compare(commitSha1: String, commitSha2: String): Int {
        val commits = listOf(ROOT_COMMIT) + case.patches.map { it.name }
        return commits.indexOf(commitSha1) compareTo commits.indexOf(commitSha2)
      }

      override fun getPatchesBetween(parent: String, child: String): List<TextFilePatch> {
        val parentIndex = case.patches.indexOfFirst { it.name == parent }
        val childIndex = case.patches.indexOfFirst { it.name == child }

        val patches = if (childIndex == -1) case.patches.subList(parentIndex + 1, case.patches.size)
        else case.patches.subList(parentIndex + 1, childIndex + 1)

        return patches.toTextFilePatches()
      }
    }
  )

  companion object {
    //region: Defining patches to test on
    // just a filler at the start of the patch-list, avoiding the conditions to quickly return the current position
    val startPatch = TestPatch(
      name = "start_patch",
      changes = listOf()
    )

    val patch1 = TestPatch(
      name = "patch1",
      changes = listOf(
        (0..<0) to (0..<10), // insert 10 lines at line 0
      )
    )

    val patch2 = TestPatch(
      name = "patch2",
      changes = listOf(
        (1..<4) to (1..<5),  // replace lines 1-3 with lines 1-4
      )
    )

    val patch3 = TestPatch(
      name = "patch3",
      changes = listOf(
        (2..<3) to (2..<4),  // replace line 2 with lines 2-3
        (4..<6) to (5..<5),  // delete lines 4-5 (shifted by 1 in result because of the insert at 2-4)
      )
    )

    // just a filler at the end of the patch-list, avoiding the conditions to quickly return the current position
    val endPatch = TestPatch(
      name = "end_patch",
      changes = listOf()
    )
    //endregion

    //region: Defining test cases
    @JvmStatic
    fun testCases(): Array<TestCase> = cases {
      // The first commit pushes all lines forward by 10, other patches shift the line +1, then -1
      withPatches(patch1, patch2, patch3) {
        0 `maps to` /* -> 10 -> 11 -> */ 10
        1 `maps to` /* -> 11 -> 12 -> */ 11
        2 `maps to` /* -> 12 -> 13 -> */ 12
        10 `reverse maps to` 0
        11 `reverse maps to` 1
        12 `reverse maps to` 2
        // in reverse, lines 0-10 collapse to 0-0
        9 `reverse maps to` 0.approximately
        5 `reverse maps to` 0.approximately
      }
      // Single patch that:
      //  - replaces 2-2 with 2-3
      //  - deletes 4-5
      withPatches(patch3) {
        0 `maps to` 0
        1 `maps to` 1
        2 `maps to` 3.approximately
        // insert at line 2 causes shift by 1
        3 `maps to` 4
        4 `maps to` 5.approximately // deleted
        5 `maps to` 5.approximately // also deleted, mapped to the same as 4
        6 `maps to` 5
        7 `maps to` 6
        6 `reverse maps to` 7
        5 `reverse maps to` 6
        // gap happens because 4-5 was deleted
        4 `reverse maps to` 3
        // approximation happens because 2-2 is replaced by 2-3
        3 `reverse maps to` 2.approximately
        2 `reverse maps to` 2.approximately
        1 `reverse maps to` 1
      }
      // Illustrations of approximate mapping:
      // relative position inside range 0-1 of line 0 = 0.5
      withPatches(TestPatch("estimation [0,1)->[0,0)", listOf((0..<1) to (0..<0)))) {
        0 `maps to` 0.approximately
      }
      withPatches(TestPatch("estimation [0,1)->[0,1)", listOf((0..<1) to (0..<1)))) {
        0 `maps to` 0.approximately
      }
      withPatches(TestPatch("estimation [0,1)->[0,2)", listOf((0..<1) to (0..<2)))) {
        0 `maps to` 1.approximately
      }
      withPatches(TestPatch("estimation [0,1)->[0,3)", listOf((0..<1) to (0..<3)))) {
        0 `maps to` 1.approximately
      }
      // relative position inside range 0-2 of line 0 = 0.333, 1 = 0.667
      withPatches(TestPatch("estimation [0,2)->[0,0)", listOf((0..<2) to (0..<0)))) {
        0 `maps to` 0.approximately
        1 `maps to` 0.approximately
      }
      withPatches(TestPatch("estimation [0,2)->[0,1)", listOf((0..<2) to (0..<1)))) {
        0 `maps to` 0.approximately
        1 `maps to` 0.approximately
      }
      withPatches(TestPatch("estimation [0,2)->[0,2)", listOf((0..<2) to (0..<2)))) {
        0 `maps to` 0.approximately
        1 `maps to` 1.approximately
      }
      withPatches(TestPatch("estimation [0,2)->[0,3)", listOf((0..<2) to (0..<3)))) {
        0 `maps to` 1.approximately // 0.333 -> line 1 in [0,1,2]
        1 `maps to` 2.approximately // 0.667 -> line 2 in [0,1,2]
      }
      // delete content is just always mapped to the first line
      withPatches(TestPatch("estimation [0,100)->[0,0)", listOf((0..<100) to (0..<0)))) {
        0 `maps to` 0.approximately
        10 `maps to` 0.approximately
        55 `maps to` 0.approximately
        99 `maps to` 0.approximately
      }
    }
    //endregion

    //region: Utilities
    data class TestPatch(
      val name: String,
      val changes: List<Pair<IntRange, IntRange>>,
      val fileStatus: FileStatus = FileStatus.MODIFIED,
    )

    fun List<TestPatch>.toTextFilePatches(): List<TextFilePatch> {
      val parentName = ROOT_COMMIT

      val patches = mutableListOf<TextFilePatch>()
      for (patch in this) {
        patches.add(TextFilePatch(Charsets.UTF_8, "\n").apply {
          if (patch.fileStatus != FileStatus.ADDED) {
            beforeVersionId = parentName
            beforeName = FILE_NAME
          }

          if (patch.fileStatus != FileStatus.DELETED) {
            afterVersionId = patch.name
            afterName = FILE_NAME
          }

          for (change in patch.changes) {
            addHunk(PatchHunk(change.first.first, change.first.last, change.second.first, change.second.last).apply {
              // our patch hunks are simple: no context lines, just changes
              repeat((change.first.last - change.first.first) + 1) { addLine(REMOVE_LINE) }
              repeat((change.second.last - change.second.first) + 1) { addLine(ADD_LINE) }
            })
          }
        })
      }
      return patches.toList()
    }

    data class TestCase(
      val patches: List<TestPatch>,
      val fromPatch: String,
      val line: Int,
      val expected: Int,
      val isEstimate: Boolean,
      val toSide: Side,
    ) {
      private val filteredPatches = patches.filter { it.name != startPatch.name && it.name != endPatch.name }

      override fun toString(): String =
        "[${filteredPatches.joinToString { it.name }}]: " +
        "$line${if (isEstimate) " approximately" else ""}${if (Side.LEFT == toSide) " reverse" else ""} maps to ${expected}"
    }

    @JvmInline
    value class Approximately(val line: Int)

    class TestCaseBuilder(
      private val actualPatches: List<TestPatch> = listOf(),
      private val fromPatch: String? = null,
    ) {
      private val firstCommit get() = startPatch.name
      private val lastCommit get() = actualPatches.last().name

      // surrounded with a start and end so that we trick the algo into actually executing
      // without a start and end patch the algo will just shortcut to return the current line index
      private val surroundedPatches = listOf(startPatch) + actualPatches + listOf(endPatch)
      var cases = mutableListOf<TestCase>()

      private fun register(case: TestCase) {
        cases.add(case)
      }

      fun withPatches(vararg patches: TestPatch, block: TestCaseBuilder.() -> Unit) {
        cases += TestCaseBuilder(patches.toList()).apply { block() }.cases
      }

      fun fromPatch(patch: String, block: TestCaseBuilder.() -> Unit) {
        cases += TestCaseBuilder(surroundedPatches, patch).apply { block() }.cases
      }

      //@formatter:off
      infix fun Int.`maps to`(line: Int) = register(TestCase(surroundedPatches, fromPatch ?: firstCommit, this, line, false, Side.RIGHT))
      infix fun Int.`reverse maps to`(line: Int) = register(TestCase(surroundedPatches, fromPatch ?: lastCommit, this, line, false, Side.LEFT))

      val Int.approximately get() = Approximately(this)
      infix fun Int.`maps to`(approximately: Approximately) = register(TestCase(surroundedPatches, fromPatch ?: firstCommit, this, approximately.line, true, Side.RIGHT))
      infix fun Int.`reverse maps to`(approximately: Approximately) = register(TestCase(surroundedPatches, fromPatch ?: lastCommit, this, approximately.line, true, Side.LEFT))
      //@formatter:on
    }

    fun cases(block: TestCaseBuilder.() -> Unit): Array<TestCase> =
      TestCaseBuilder().apply { block() }.cases.toTypedArray()

    val REMOVE_LINE = PatchLine(PatchLine.Type.REMOVE, "").apply { isSuppressNewLine = true }
    val ADD_LINE = PatchLine(PatchLine.Type.ADD, "").apply { isSuppressNewLine = true }

    const val ROOT_COMMIT = "root"
    const val FILE_NAME = "file"
    //endregion
  }
}