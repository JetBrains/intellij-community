// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.cce.report

import com.intellij.cce.core.Lookup
import com.intellij.cce.core.Session
import com.intellij.cce.core.TokenProperties
import com.intellij.cce.evaluation.data.ArtifactFile
import com.intellij.cce.evaluation.data.Execution
import com.intellij.cce.workspace.info.FileEvaluationInfo
import com.intellij.cce.workspace.info.FileSessionsInfo
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path

class ArtifactsToReportTest {
  @Test
  fun `copies artifact files to report directory`(@TempDir tempDir: Path) {
    val source = tempDir.resolve("source.txt")
    Files.writeString(source, "artifact file content")

    val lookup = Execution.ARTIFACTS_FILES.placement.dump(
      lookup(),
      listOf(ArtifactFile(target = "nested/result.txt", source = source.toString()))
    )

    ArtifactsToReport(tempDir.resolve("report").toString(), "filter", "comparison")
      .generateFileReport(listOf(fileEvaluationInfo(lookup)))

    val copied = tempDir.resolve("report/comparison/artifacts/filter/nested/result.txt")
    assertEquals("artifact file content", Files.readString(copied))
  }

  @Test
  fun `does not copy artifact files outside report directory`(@TempDir tempDir: Path) {
    val source = tempDir.resolve("source.txt")
    Files.writeString(source, "artifact file content")

    val lookup = Execution.ARTIFACTS_FILES.placement.dump(
      lookup(),
      listOf(ArtifactFile(target = "../outside.txt", source = source.toString()))
    )

    ArtifactsToReport(tempDir.resolve("report").toString(), "filter", "comparison")
      .generateFileReport(listOf(fileEvaluationInfo(lookup)))

    assertFalse(Files.exists(tempDir.resolve("report/comparison/artifacts/outside.txt")))
  }

  private fun fileEvaluationInfo(lookup: Lookup): FileEvaluationInfo {
    val session = Session(0, "", 0, TokenProperties.UNKNOWN)
    session.addLookup(lookup)
    return FileEvaluationInfo(
      sessionsInfo = FileSessionsInfo("project", "file.kt", "", listOf(session)),
      metrics = emptyList(),
      evaluationType = "evaluation"
    )
  }

  private fun lookup(): Lookup = Lookup(
    prefix = "",
    offset = 0,
    suggestions = emptyList(),
    latency = 0,
    isNew = true
  )
}
