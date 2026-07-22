// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("HardCodedStringLiteral")

package org.jetbrains.idea.devkit.inspections.remotedev

import com.intellij.codeInsight.intention.LowPriorityAction
import com.intellij.codeInspection.LocalQuickFix
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.project.IntelliJProjectUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.guessProjectDir
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.writeText
import com.fasterxml.jackson.core.json.JsonReadFeature
import com.fasterxml.jackson.databind.json.JsonMapper
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import org.jetbrains.idea.devkit.DevKitBundle.message
import java.io.IOException

internal const val PROJECT_BASELINE_VERSION_FILE_NAME: String = "SplitModeProjectBaselineVersion.json5"
internal const val PROJECT_BASELINE_VERSION_RELATIVE_PATH: String =
  "community/plugins/devkit/devkit-core/resources/remotedevInspectionData/$PROJECT_BASELINE_VERSION_FILE_NAME"

@Service(Service.Level.PROJECT)
internal class SplitModeInspectionExclusionsService(private val project: Project) {
  companion object {
    fun getInstance(project: Project): SplitModeInspectionExclusionsService = project.service()
  }

  fun createCommonSuppressionQuickFixes(): Array<LocalQuickFix> {
    if (!isBaselineFixAvailable()) return LocalQuickFix.EMPTY_ARRAY
    return arrayOf(IncreaseSplitModeProjectBaselineVersionFix())
  }

  fun increaseProjectBaselineVersion(): VirtualFile? {
    val baselineVersionFile = findOrCreateProjectBaselineVersionFile() ?: return null
    val currentFile = parseProjectBaselineVersionFile(VfsUtilCore.loadText(baselineVersionFile))
    val updatedFile = currentFile.copy(
      currentProjectBaselineVersion = currentFile.currentProjectBaselineVersion + 1,
    )
    baselineVersionFile.writeText(createProjectBaselineVersionJson5(updatedFile))
    return baselineVersionFile
  }

  private fun isBaselineFixAvailable(): Boolean {
    return IntelliJProjectUtil.isIntelliJPlatformProject(project) && getProjectRoot() != null
  }

  private fun getProjectRoot(): VirtualFile? {
    return project.guessProjectDir()
  }

  private fun findOrCreateProjectBaselineVersionFile(): VirtualFile? {
    val projectRoot = getProjectRoot() ?: return null
    return try {
      val parentDirectory = VfsUtil.createDirectoryIfMissing(projectRoot, EXCLUSIONS_DIRECTORY_RELATIVE_PATH)
      parentDirectory.findChild(PROJECT_BASELINE_VERSION_FILE_NAME)
      ?: parentDirectory.createChildData(this, PROJECT_BASELINE_VERSION_FILE_NAME).also { file ->
        file.writeText(createProjectBaselineVersionJson5(SplitModeProjectBaselineVersionFile(currentProjectBaselineVersion = 0)))
      }
    }
    catch (e: IOException) {
      LOG.warn("Cannot create $PROJECT_BASELINE_VERSION_RELATIVE_PATH", e)
      null
    }
  }

  private fun parseProjectBaselineVersionFile(text: String): SplitModeProjectBaselineVersionFile {
    if (text.isBlank()) {
      return SplitModeProjectBaselineVersionFile(currentProjectBaselineVersion = 0)
    }

    return try {
      val normalizedJson = json5.readTree(text).toString()
      json.decodeFromString(normalizedJson)
    }
    catch (e: SerializationException) {
      LOG.warn("Cannot parse $PROJECT_BASELINE_VERSION_RELATIVE_PATH", e)
      SplitModeProjectBaselineVersionFile(currentProjectBaselineVersion = 0)
    }
    catch (e: IOException) {
      LOG.warn("Cannot parse $PROJECT_BASELINE_VERSION_RELATIVE_PATH", e)
      SplitModeProjectBaselineVersionFile(currentProjectBaselineVersion = 0)
    }
    catch (e: IllegalArgumentException) {
      LOG.warn("Cannot parse $PROJECT_BASELINE_VERSION_RELATIVE_PATH", e)
      SplitModeProjectBaselineVersionFile(currentProjectBaselineVersion = 0)
    }
  }
}

private class IncreaseSplitModeProjectBaselineVersionFix : LocalQuickFix, LowPriorityAction {
  override fun getName(): String = familyName

  override fun getFamilyName(): String {
    return message("inspection.remote.dev.increase.split.mode.baseline.fix.name")
  }

  override fun startInWriteAction(): Boolean = false

  override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
    val baselineVersionFile = WriteCommandAction.writeCommandAction(project)
      .withName(message("inspection.remote.dev.increase.split.mode.baseline.command.name"))
      .compute<VirtualFile?, Throwable> {
        SplitModeInspectionExclusionsService.getInstance(project).increaseProjectBaselineVersion()
      }

    if (baselineVersionFile == null) {
      return
    }

    OpenFileDescriptor(project, baselineVersionFile, 0, 0).navigate(true)
  }
}

@Serializable
private data class SplitModeProjectBaselineVersionFile(
  @SerialName("currentProjectBaselineVersion")
  val currentProjectBaselineVersion: Int,
)

private val LOG: Logger = logger<SplitModeInspectionExclusionsService>()

private const val EXCLUSIONS_DIRECTORY_RELATIVE_PATH: String = "community/plugins/devkit/devkit-core/resources/remotedevInspectionData"
private val json5 = JsonMapper.builder()
  .enable(
    JsonReadFeature.ALLOW_JAVA_COMMENTS,
    JsonReadFeature.ALLOW_SINGLE_QUOTES,
    JsonReadFeature.ALLOW_UNQUOTED_FIELD_NAMES,
    JsonReadFeature.ALLOW_TRAILING_COMMA,
  )
  .build()

private fun createProjectBaselineVersionJson5(file: SplitModeProjectBaselineVersionFile): String {
  return """
    // The file carries the current project baseline version for Split Mode Compatibility safe-push checks.
    // Increase currentProjectBaselineVersion only when incremental Split Mode Compatibility tests incorrectly
    // report pre-existing violations after large refactorings.
    // Changing this value is a legal bypass: the affected safe push may skip the incremental Split Mode
    // Compatibility tests for that run, allowing the refactoring to proceed while the baseline catches up.
    // Do not use it for real new violations. Fix those instead; see Split Mode documentation IJPL-A-632.
    // Contact the RemDev team in #ij-remote-dev if unsure.
  """.trimIndent() + "\n" + json.encodeToString(file) + "\n"
}

private val json = Json {
  ignoreUnknownKeys = true
  isLenient = true
  prettyPrint = true
  encodeDefaults = false
}
