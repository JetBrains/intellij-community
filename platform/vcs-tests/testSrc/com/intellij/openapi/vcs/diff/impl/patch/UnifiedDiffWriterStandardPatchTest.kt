// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs.diff.impl.patch

import com.intellij.diff.HeavyDiffTestCase
import com.intellij.openapi.diff.impl.patch.IdeaTextPatchBuilder
import com.intellij.openapi.diff.impl.patch.PatchEP
import com.intellij.openapi.diff.impl.patch.UnifiedDiffWriter
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.changes.Change
import com.intellij.openapi.vcs.changes.CommitContext
import com.intellij.openapi.vcs.changes.SimpleContentRevision
import com.intellij.openapi.vcs.changes.patch.PatchWriter
import com.intellij.vcsUtil.VcsUtil
import java.io.StringWriter
import java.nio.file.Paths

class UnifiedDiffWriterStandardPatchTest : HeavyDiffTestCase() {
  fun testPatchHeaders() {
    val basePath = Paths.get("/base/")
    val filePath = VcsUtil.getFilePath("/base/some/file.txt", false)

    val change = Change(SimpleContentRevision("a\n", filePath, "1"),
                        SimpleContentRevision("b\n", filePath, "2"))
    val patches = IdeaTextPatchBuilder.buildPatch(project, listOf(change), basePath, false)

    val standardFormats = booleanArrayOf(false, true)
    val includeAdditionalInfo = booleanArrayOf(false, true)
    for (standardFormat in standardFormats) {
      val commitContext: CommitContext? = if (standardFormat) {
        CommitContext().apply { putUserData(PatchWriter.STANDARD_PATCH_FORMAT_KEY, true) }
      }
      else {
        null
      }
      for (includeInfo in includeAdditionalInfo) {
        val ep = object : PatchEP {
          override fun getName(): String = "TestPatchInfo"

          override fun provideContent(project: Project, path: String, commitContext: CommitContext?): CharSequence? {
            return if (includeInfo) "test content" else ""
          }

          override fun consumeContentBeforePatchApplied(project: Project, path: String, content: CharSequence, commitContext: CommitContext?) {
          }
        }

        val writer = StringWriter()
        UnifiedDiffWriter.write(project, basePath, patches, writer, "\n", commitContext, listOf(ep))
        val text = writer.toString()
        assertTrue(text.contains("diff --git"))
        assertEquals(!standardFormat,
                     text.contains("Index:"))
        assertEquals(includeInfo && !standardFormat,
                     text.contains(UnifiedDiffWriter.ADDITIONAL_PREFIX) && text.contains(UnifiedDiffWriter.ADD_INFO_HEADER))
      }
    }
  }
}
