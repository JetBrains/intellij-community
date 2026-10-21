// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.mergerequest.data

import com.intellij.collaboration.util.RefComparisonChange
import com.intellij.openapi.diff.impl.patch.PatchReader
import com.intellij.openapi.diff.impl.patch.TextFilePatch
import git4idea.changes.GitBranchComparisonResult
import git4idea.changes.GitTextFilePatchWithHistory
import io.mockk.every
import io.mockk.mockk
import org.assertj.core.api.Assertions.assertThat
import org.jetbrains.plugins.gitlab.api.dto.GitLabDiffDTO
import org.jetbrains.plugins.gitlab.api.dto.filePath
import org.jetbrains.plugins.gitlab.mergerequest.data.GitLabMergeRequestParsedChanges.EmptyPatchReason
import org.junit.jupiter.api.Test

private const val LOCATION = "MR 1"

class GitLabDiffEmptyReasonTest {

  @Test
  fun `a change reads the reason of the path of its patch`() {
    val change = change()
    val patch = localPatch("lib/big.rb")
    val parsedChanges = parsedChanges(
      patches = mapOf(change to patch),
      patchesWithEmptyReasons = listOf(PatchWithEmptyReason(patch, EmptyPatchReason.TOO_LARGE)),
    )

    assertThat(parsedChanges.getEmptyPatchReason(change)).isEqualTo(EmptyPatchReason.TOO_LARGE)
  }

  @Test
  fun `a deleted file reads the reason of its old path`() {
    val change = change()
    val patch = localPatch("lib/gone.rb").apply { afterName = null }
    val parsedChanges = parsedChanges(
      patches = mapOf(change to patch),
      patchesWithEmptyReasons = listOf(PatchWithEmptyReason(patch, EmptyPatchReason.COLLAPSED_NO_LOCAL)),
    )

    assertThat(parsedChanges.getEmptyPatchReason(change)).isEqualTo(EmptyPatchReason.COLLAPSED_NO_LOCAL)
  }

  @Test
  fun `a commentable change has no reason when emptyReason is null`() {
    val change = change()
    val patch = localPatch("lib/plain.rb")
    val otherPatch = localPatch("lib/big.rb")
    val parsedChanges = parsedChanges(
      patches = mapOf(change to patch),
      patchesWithEmptyReasons = listOf(
        PatchWithEmptyReason(patch, null),
        PatchWithEmptyReason(otherPatch, EmptyPatchReason.TOO_LARGE),
      ),
    )

    assertThat(parsedChanges.getEmptyPatchReason(change)).isNull()
  }

  @Test
  fun `a merge request with no gap reports no reason`() {
    val change = change()
    val patch = localPatch("lib/big.rb")
    val parsedChanges = parsedChanges(
      patches = mapOf(change to patch),
      patchesWithEmptyReasons = listOf(PatchWithEmptyReason(patch, null)),
    )

    assertThat(parsedChanges.getEmptyPatchReason(change)).isNull()
  }

  @Test
  fun `a change with no patch reports no reason`() {
    val patch = localPatch("lib/big.rb")
    val parsedChanges = parsedChanges(
      patches = emptyMap(),
      patchesWithEmptyReasons = listOf(PatchWithEmptyReason(patch, EmptyPatchReason.TOO_LARGE)),
    )

    assertThat(parsedChanges.getEmptyPatchReason(change())).isNull()
  }

  @Test
  fun `filePath extension on GitLabDiffDTO resolves paths correctly`() {
    val normalDto = GitLabDiffDTO(aMode = "100644", bMode = "100644", diff = "", deletedFile = false, newFile = false, renamedFile = false, newPath = "new/path.rb", oldPath = "old/path.rb")
    val deletedDto = GitLabDiffDTO(aMode = "100644", bMode = "000000", diff = "", deletedFile = true, newFile = false, renamedFile = false, newPath = null, oldPath = "old/path.rb")
    val newDto = GitLabDiffDTO(aMode = "000000", bMode = "100644", diff = "", deletedFile = false, newFile = true, renamedFile = false, newPath = "new/path.rb", oldPath = null)

    assertThat(normalDto.filePath).isEqualTo("new/path.rb")
    assertThat(deletedDto.filePath).isEqualTo("old/path.rb")
    assertThat(newDto.filePath).isEqualTo("new/path.rb")
  }

  @Test
  fun `a too large added or deleted file reports the size limit`() {
    val added = GitLabDiffDTO(aMode = "0", bMode = "100644", diff = "", deletedFile = false, newFile = true, renamedFile = false,
                              newPath = "huge.txt", oldPath = "huge.txt", collapsed = false, tooLarge = true)
    val deleted = GitLabDiffDTO(aMode = "100644", bMode = "0", diff = "", deletedFile = true, newFile = false, renamedFile = false,
                                newPath = "huge_deleted.txt", oldPath = "huge_deleted.txt", collapsed = false, tooLarge = true)

    assertThat(added.needsLocalPatch).isFalse()
    assertThat(deleted.needsLocalPatch).isFalse()

    val patches = GitLabMergeRequestChangesImpl.processDiffs(listOf(added, deleted), emptyMap(), LOCATION)

    assertThat(patches.map { it.emptyReason }).containsExactly(EmptyPatchReason.TOO_LARGE, EmptyPatchReason.TOO_LARGE)
  }

  @Test
  fun `a collapsed file takes the local patch`() {
    val withBody = GitLabDiffDTO(aMode = "100644", bMode = "100644", diff = "@@ -1,1 +1,1 @@\n-old\n+new\n", deletedFile = false,
                                 newFile = false, renamedFile = false, newPath = "lib/small.rb", oldPath = "lib/small.rb",
                                 collapsed = false, tooLarge = false)
    val rebuilt = GitLabDiffDTO(aMode = "100644", bMode = "100644", diff = "", deletedFile = false, newFile = false, renamedFile = false,
                                newPath = "lib/big.rb", oldPath = "lib/big.rb", collapsed = true, tooLarge = false)
    val notLocal = GitLabDiffDTO(aMode = "100644", bMode = "100644", diff = "", deletedFile = false, newFile = false, renamedFile = false,
                                 newPath = "lib/lost.rb", oldPath = "lib/lost.rb", collapsed = true, tooLarge = false)
    val rebuiltPatch = localPatch("lib/big.rb")
    val diffs = listOf(withBody, rebuilt, notLocal)

    assertThat(diffs.filter { it.needsLocalPatch }).containsExactly(rebuilt, notLocal)

    val patches = GitLabMergeRequestChangesImpl.processDiffs(diffs, mapOf(rebuilt to rebuiltPatch), LOCATION)

    assertThat(patches[1].patch).isSameAs(rebuiltPatch)
    assertThat(patches.map { it.emptyReason }).containsExactly(null, null, EmptyPatchReason.COLLAPSED_NO_LOCAL)
  }

  /**
   * `git diff -M` pairs the two sides of a rename only when the pathspec holds both paths.
   */
  @Test
  fun `the local diff of a renamed file holds both paths`() {
    val renamed = GitLabDiffDTO(aMode = "100644", bMode = "100644", diff = "", deletedFile = false, newFile = false, renamedFile = true,
                                newPath = "lib/moved.rb", oldPath = "lib/plain.rb", collapsed = true, tooLarge = false)
    val added = GitLabDiffDTO(aMode = "0", bMode = "100644", diff = "", deletedFile = false, newFile = true, renamedFile = false,
                              newPath = "lib/new.rb", oldPath = "lib/new.rb", collapsed = true, tooLarge = false)
    val deleted = GitLabDiffDTO(aMode = "100644", bMode = "0", diff = "", deletedFile = true, newFile = false, renamedFile = false,
                                newPath = "lib/gone.rb", oldPath = "lib/gone.rb", collapsed = true, tooLarge = false)

    assertThat(renamed.localDiffPaths).containsExactly("lib/plain.rb", "lib/moved.rb")
    assertThat(added.localDiffPaths).containsExactly("lib/new.rb")
    assertThat(deleted.localDiffPaths).containsExactly("lib/gone.rb")
  }

  companion object {
    private fun change(): RefComparisonChange = mockk()

    private fun parsedChanges(
      patches: Map<RefComparisonChange, TextFilePatch>,
      patchesWithEmptyReasons: List<PatchWithEmptyReason>,
    ): GitLabMergeRequestParsedChanges {
      val delegate = mockk<GitBranchComparisonResult> {
        every { patchesByChange } returns patches.mapValues { (_, patch) -> GitTextFilePatchWithHistory(patch, true, mockk()) }
      }
      return GitLabMergeRequestParsedChangesImpl(delegate, patchesWithEmptyReasons)
    }

    private fun localPatch(beforePath: String, afterPath: String = beforePath): TextFilePatch {
      val text = buildString {
        append("diff --git a/").append(beforePath).append(" b/").append(afterPath).append("\n")
        append("--- a/").append(beforePath).append("\n")
        append("+++ b/").append(afterPath).append("\n")
        append("@@ -1,1 +1,1 @@\n")
        append("-old\n")
        append("+new\n")
      }
      return PatchReader(text).readTextPatches().first()
    }
  }
}
