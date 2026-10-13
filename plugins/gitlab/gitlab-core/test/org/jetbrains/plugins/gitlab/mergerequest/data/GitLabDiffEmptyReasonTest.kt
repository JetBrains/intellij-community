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
