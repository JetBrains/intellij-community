// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("HardCodedStringLiteral")

package org.jetbrains.idea.devkit.inspections.remotedev

import com.intellij.codeInsight.intention.FileModifier.SafeTypeForPreview
import com.intellij.codeInsight.intention.LowPriorityAction
import com.intellij.codeInspection.LocalQuickFix
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.project.IntelliJProjectUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.guessProjectDir
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.writeText
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import org.jetbrains.idea.devkit.DevKitBundle.message
import java.io.IOException

internal object SplitModeInspectionExclusions {
  const val EXCLUSIONS_FILE_NAME: String = "DevKitSplitModeInspectionExclusions.json"
  const val EXCLUSIONS_RELATIVE_PATH: String =
    "community/plugins/devkit/devkit-core/resources/remotedevInspectionData/$EXCLUSIONS_FILE_NAME"

  const val SPLIT_MODE_API_USAGE_SHORT_NAME: String = "SplitModeApiUsage"
  const val SPLIT_MODE_XML_API_USAGE_SHORT_NAME: String = "SplitModeXmlApiUsage"
  const val SPLIT_MODE_MIXED_DEPENDENCIES_SHORT_NAME: String = "SplitModeMixedDependencies"
  const val MISSING_RUNTIME_DEPENDENCY_SHORT_NAME: String = "MissingFrontendOrBackendRuntimeDependency"

  fun getInstance(project: Project): SplitModeInspectionExclusionsService = project.service()

  fun createProblem(
    inspectionShortName: String,
    element: PsiElement,
  ): SplitModeInspectionExclusionProblem? {
    val file = element.containingFile ?: return null
    val filePath = getProjectRelativePath(file) ?: return null
    val line = getLineNumber(file, element) ?: return null
    return SplitModeInspectionExclusionProblem(
      inspection = inspectionShortName,
      file = filePath,
      line = line,
      reason = "",
    )
  }

  fun addExclusionFixLast(
    project: Project,
    fixes: Array<LocalQuickFix>,
    problem: SplitModeInspectionExclusionProblem?,
  ): Array<LocalQuickFix> {
    if (problem == null || !isExclusionFixAvailable(project)) return fixes
    return fixes + AddToSplitModeInspectionExclusionsFix(problem)
  }

  fun isExclusionFixAvailable(project: Project): Boolean {
    return IntelliJProjectUtil.isIntelliJPlatformProject(project) && getProjectRoot(project) != null
  }

  fun getProjectRoot(project: Project): VirtualFile? {
    return project.guessProjectDir()
  }

  fun getProjectRelativePath(file: PsiFile): String? {
    val virtualFile = file.originalFile.virtualFile ?: file.virtualFile ?: return null
    val projectRoot = getProjectRoot(file.project) ?: return null
    return VfsUtilCore.getRelativePath(virtualFile, projectRoot, '/')
  }

  private fun getLineNumber(file: PsiFile, element: PsiElement): Int? {
    val document = PsiDocumentManager.getInstance(file.project).getDocument(file)
                   ?: file.viewProvider.document
                   ?: return null
    return document.getLineNumber(element.textRange.startOffset) + 1
  }
}

@SafeTypeForPreview
internal data class SplitModeInspectionExclusionProblem(
  val inspection: String,
  val file: String,
  val line: Int,
  val reason: String = "",
)

@Service(Service.Level.PROJECT)
internal class SplitModeInspectionExclusionsService(private val project: Project) {
  @Volatile
  private var cachedSnapshot: CachedSplitModeInspectionExclusionsSnapshot? = null

  fun isExcluded(element: PsiElement, inspectionShortName: String): Boolean {
    val problem = SplitModeInspectionExclusions.createProblem(inspectionShortName, element) ?: return false
    return isExcluded(problem)
  }

  fun isExcluded(problem: SplitModeInspectionExclusionProblem): Boolean {
    return getSnapshot().exclusions.any { it.matches(problem) }
  }

  fun appendExclusion(problem: SplitModeInspectionExclusionProblem): VirtualFile? {
    val exclusionsFile = findOrCreateExclusionsFile() ?: return null
    val currentFile = parseExclusionsFile(readText(exclusionsFile))
    val newEntry = problem.toEntry()
    if (currentFile.exclusions.any { it.matches(newEntry) }) {
      return exclusionsFile
    }

    val updatedFile = currentFile.copy(exclusions = currentFile.exclusions + newEntry)
    exclusionsFile.writeText(json.encodeToString(updatedFile) + "\n")
    cachedSnapshot = null
    return exclusionsFile
  }

  fun findExclusionsFile(): VirtualFile? {
    return SplitModeInspectionExclusions.getProjectRoot(project)
      ?.findFileByRelativePath(SplitModeInspectionExclusions.EXCLUSIONS_RELATIVE_PATH)
  }

  private fun getSnapshot(): SplitModeInspectionExclusionsSnapshot {
    val exclusionsFile = findExclusionsFile()
    val exclusionsText = exclusionsFile?.let { readText(it) }
    val cacheKey = createCacheKey(exclusionsFile, exclusionsText)
    cachedSnapshot?.let { cached ->
      if (cached.cacheKey == cacheKey) {
        return cached.snapshot
      }
    }

    val snapshot = if (exclusionsFile == null) {
      SplitModeInspectionExclusionsSnapshot(emptyList())
    }
    else {
      SplitModeInspectionExclusionsSnapshot(parseExclusionsFile(exclusionsText ?: "").exclusions)
    }
    cachedSnapshot = CachedSplitModeInspectionExclusionsSnapshot(cacheKey, snapshot)
    return snapshot
  }

  private fun createCacheKey(exclusionsFile: VirtualFile?, exclusionsText: String?): SplitModeInspectionExclusionsCacheKey {
    if (exclusionsFile == null) {
      return SplitModeInspectionExclusionsCacheKey(null, -1, -1, -1)
    }
    val document = FileDocumentManager.getInstance().getCachedDocument(exclusionsFile)
    return SplitModeInspectionExclusionsCacheKey(
      file = exclusionsFile,
      fileModificationStamp = exclusionsFile.modificationStamp,
      documentModificationStamp = document?.modificationStamp ?: -1,
      contentHash = exclusionsText.hashCode(),
    )
  }

  private fun findOrCreateExclusionsFile(): VirtualFile? {
    val projectRoot = SplitModeInspectionExclusions.getProjectRoot(project) ?: return null
    return try {
      val parentDirectory = VfsUtil.createDirectoryIfMissing(projectRoot, EXCLUSIONS_DIRECTORY_RELATIVE_PATH)
      parentDirectory.findChild(SplitModeInspectionExclusions.EXCLUSIONS_FILE_NAME)
      ?: parentDirectory.createChildData(this, SplitModeInspectionExclusions.EXCLUSIONS_FILE_NAME).also { file ->
        file.writeText(INITIAL_EXCLUSIONS_JSON)
      }
    }
    catch (e: IOException) {
      LOG.warn("Cannot create ${SplitModeInspectionExclusions.EXCLUSIONS_RELATIVE_PATH}", e)
      null
    }
  }

  private fun readText(file: VirtualFile): String {
    val fileDocumentManager = FileDocumentManager.getInstance()
    val document = fileDocumentManager.getCachedDocument(file)
    return if (document != null && fileDocumentManager.isDocumentUnsaved(document)) document.text else VfsUtilCore.loadText(file)
  }

  private fun parseExclusionsFile(text: String): SplitModeInspectionExclusionsFile {
    if (text.isBlank()) {
      return SplitModeInspectionExclusionsFile()
    }

    return try {
      json.decodeFromString(text)
    }
    catch (e: SerializationException) {
      LOG.warn("Cannot parse ${SplitModeInspectionExclusions.EXCLUSIONS_RELATIVE_PATH}", e)
      SplitModeInspectionExclusionsFile()
    }
    catch (e: IllegalArgumentException) {
      LOG.warn("Cannot parse ${SplitModeInspectionExclusions.EXCLUSIONS_RELATIVE_PATH}", e)
      SplitModeInspectionExclusionsFile()
    }
  }

  private fun SplitModeInspectionExclusionEntry.matches(problem: SplitModeInspectionExclusionProblem): Boolean {
    if (inspection != problem.inspection) return false
    if (file != problem.file) return false
    if (line != problem.line) return false
    return true
  }

  private fun SplitModeInspectionExclusionEntry.matches(other: SplitModeInspectionExclusionEntry): Boolean {
    if (inspection != other.inspection) return false
    if (file != other.file) return false
    if (line != other.line) return false
    return true
  }

  private fun SplitModeInspectionExclusionProblem.toEntry(): SplitModeInspectionExclusionEntry {
    return SplitModeInspectionExclusionEntry(
      inspection = inspection,
      file = file,
      line = line,
      reason = reason,
    )
  }
}

private class AddToSplitModeInspectionExclusionsFix(
  private val problem: SplitModeInspectionExclusionProblem,
) : LocalQuickFix, LowPriorityAction {
  override fun getName(): String = familyName

  @Suppress("DialogTitleCapitalization")
  override fun getFamilyName(): String {
    return message("inspection.remote.dev.add.to.split.mode.exclusions.fix.name")
  }

  override fun startInWriteAction(): Boolean = false

  override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
    val exclusionsFile = WriteCommandAction.writeCommandAction(project)
      .withName(message("inspection.remote.dev.add.to.split.mode.exclusions.command.name"))
      .compute<VirtualFile?, Throwable> {
        SplitModeInspectionExclusions.getInstance(project).appendExclusion(problem)
      }

    if (exclusionsFile == null) {
      return
    }

    val document = FileDocumentManager.getInstance().getDocument(exclusionsFile)
    val lastLine = maxOf((document?.lineCount ?: 1) - 1, 0)
    OpenFileDescriptor(project, exclusionsFile, lastLine, 0).navigate(true)
  }
}

private data class SplitModeInspectionExclusionsSnapshot(
  val exclusions: List<SplitModeInspectionExclusionEntry>,
)

private data class CachedSplitModeInspectionExclusionsSnapshot(
  val cacheKey: SplitModeInspectionExclusionsCacheKey,
  val snapshot: SplitModeInspectionExclusionsSnapshot,
)

private data class SplitModeInspectionExclusionsCacheKey(
  val file: VirtualFile?,
  val fileModificationStamp: Long,
  val documentModificationStamp: Long,
  val contentHash: Int,
)

@Serializable
internal data class SplitModeInspectionExclusionsFile(
  @SerialName("exclusions")
  val exclusions: List<SplitModeInspectionExclusionEntry> = emptyList(),
)

@Serializable
internal data class SplitModeInspectionExclusionEntry(
  @SerialName("inspection")
  val inspection: String,

  @SerialName("file")
  val file: String = "",

  @SerialName("line")
  val line: Int = -1,

  @SerialName("reason")
  val reason: String? = null,
)

private val LOG: Logger = logger<SplitModeInspectionExclusionsService>()

private const val EXCLUSIONS_DIRECTORY_RELATIVE_PATH: String = "community/plugins/devkit/devkit-core/resources/remotedevInspectionData"
private const val INITIAL_EXCLUSIONS_JSON: String = "{ \"exclusions\": [] }\n"

private val json = Json {
  ignoreUnknownKeys = true
  isLenient = true
  prettyPrint = true
  encodeDefaults = false
}
