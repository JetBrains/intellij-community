// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.cce.report

import com.intellij.cce.core.Lookup
import com.intellij.cce.core.Session
import com.intellij.cce.evaluable.AIA_EVAL_ARTIFACT
import com.intellij.cce.evaluable.AIA_EVAL_ARTIFACT_NAME
import com.intellij.cce.evaluable.AIA_NAME
import com.intellij.cce.evaluation.data.ArtifactFile
import com.intellij.cce.evaluation.data.DataProps
import com.intellij.cce.evaluation.data.Execution
import com.intellij.cce.metric.MetricInfo
import com.intellij.cce.workspace.info.FileErrorInfo
import com.intellij.cce.workspace.info.FileEvaluationInfo
import com.intellij.openapi.diagnostic.Logger
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.StandardCopyOption

class ArtifactsToReport(
  outputDir: String,
  filterName: String,
  comparisonFilterName: String,
) : FullReportGenerator {

  override val type: String = "artifacts"

  private val dir: Path = Paths.get(outputDir, comparisonFilterName, type, filterName).normalize().also { Files.createDirectories(it) }

  override fun generateFileReport(sessions: List<FileEvaluationInfo>) {
    if (sessions.isEmpty()) return
    try {
      for (fileEvaluation in sessions) {
        for (session in fileEvaluation.sessionsInfo.sessions) {
          for (lookup in session.lookups) {
            saveArtifact(lookup)
            saveArtifactFiles(session, lookup)
          }
        }
      }
    }
    catch (e: Exception) {
      LOG.warn("Failed to save artifacts", e)
    }
  }

  private fun saveArtifact(lookup: Lookup) {
    val artifact = lookup.additionalInfo[AIA_EVAL_ARTIFACT] as? String
                   ?: return

    val name = lookup.additionalInfo[AIA_NAME] as? String
    val artifactName = lookup.additionalInfo[AIA_EVAL_ARTIFACT_NAME] as? String

    val artifactFileName = artifactName
                           ?: name?.let { "${it}.artifact" }
                           ?: "artifact"
    
    val artifactPath = dir.resolve(artifactFileName)
    Files.writeString(artifactPath, artifact)
  }

  private fun saveArtifactFiles(session: Session, lookup: Lookup) {
    val props = DataProps(null, null, session, lookup)
    for (artifactFile in Execution.ARTIFACTS_FILES.placement.restore(props)) {
      copyArtifactFile(artifactFile)
    }
  }

  private fun copyArtifactFile(artifactFile: ArtifactFile) {
    val targetPath = resolveTarget(artifactFile.target) ?: return
    targetPath.parent?.let { Files.createDirectories(it) }
    Files.copy(Paths.get(artifactFile.source), targetPath, StandardCopyOption.REPLACE_EXISTING)
  }

  private fun resolveTarget(target: String): Path? {
    if (target.isBlank()) {
      LOG.warn("Failed to save artifact file: target path is blank")
      return null
    }

    val relativeTarget = Paths.get(target)
    if (relativeTarget.isAbsolute) {
      LOG.warn("Failed to save artifact file: target path should be relative: $target")
      return null
    }

    val targetPath = dir.resolve(relativeTarget).normalize()
    if (!targetPath.startsWith(dir)) {
      LOG.warn("Failed to save artifact file: target path escapes report directory: $target")
      return null
    }
    return targetPath
  }

  override fun generateErrorReports(errors: List<FileErrorInfo>): Unit = Unit

  override fun generateGlobalReport(globalMetrics: List<MetricInfo>): Path {
    return dir
  }

  companion object {
    private val LOG = Logger.getInstance(ArtifactsToReport::class.java)
  }
}
