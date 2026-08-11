// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.devkit.mcp

import com.intellij.codeInspection.InspectionEngine
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.codeInspection.ProblemDescriptorBase
import com.intellij.codeInspection.ProblemDescriptorUtil
import com.intellij.codeInspection.ex.LocalInspectionToolWrapper
import com.intellij.mcpserver.mcpFail
import com.intellij.mcpserver.project
import com.intellij.mcpserver.util.resolveInProject
import com.intellij.openapi.application.smartReadAction
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.progress.EmptyProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.checkCanceled
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.openapi.vfs.VirtualFileVisitor
import com.intellij.platform.ide.progress.withBackgroundProgress
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import com.intellij.util.PairProcessor
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.Duration.Companion.milliseconds

private val SPLIT_MODE_COMPATIBILITY_INSPECTION_IDS = listOf(
  "SplitModeApiUsage",
  "SplitModeXmlApiUsage",
  "SplitModeMixedDependencies",
  "SplitModeImplicitModuleKind",
  "MissingFrontendOrBackendRuntimeDependency",
)

private val SPLIT_MODE_COMPATIBILITY_FILE_EXTENSIONS = setOf("java", "kt", "kts", "xml")

internal suspend fun collectSplitModeCompatibilityIssues(
  directoryPath: String,
  analysisTimeout: Int,
): DevKitMcpToolset.SplitModeCompatibilityIssuesResult {
  val project = currentCoroutineContext().project
  val resolvedDirectoryPath = project.resolveInProject(directoryPath)
  val directory = VirtualFileManager.getInstance().findFileByNioPath(resolvedDirectoryPath)
                  ?: VirtualFileManager.getInstance().refreshAndFindFileByNioPath(resolvedDirectoryPath)
                  ?: mcpFail("Directory not found: $directoryPath")
  if (!directory.isDirectory) {
    mcpFail("Not a directory: $directoryPath")
  }

  loadSplitModeRestrictions(project)

  var result = SplitModeCompatibilityInspectionRunResult(emptyList(), 0)
  val timedOut = withTimeoutOrNull(analysisTimeout.milliseconds) {
    @Suppress("HardCodedStringLiteral")
    withBackgroundProgress(project, "Collecting Split Mode compatibility issues", true) {
      val inspectionFiles = smartReadAction(project) {
        collectInspectionFiles(directory, project)
      }

      val issues = mutableListOf<DevKitMcpToolset.SplitModeCompatibilityIssue>()
      var inspectedFileCount = 0
      for (virtualFile in inspectionFiles) {
        checkCanceled()
        val fileIssues = smartReadAction(project) {
          val psiManager = PsiManager.getInstance(project)
          val psiFile = psiManager.findFile(virtualFile) ?: return@smartReadAction null
          val tools = getSplitModeCompatibilityInspectionTools(project)
          inspectSplitModeCompatibilityFile(project, psiFile, tools)
        } ?: continue
        inspectedFileCount++
        issues += fileIssues
      }
      result = SplitModeCompatibilityInspectionRunResult(issues, inspectedFileCount)
    }
  } == null

  return DevKitMcpToolset.SplitModeCompatibilityIssuesResult(
    directoryPath = directoryPath,
    inspectionIds = SPLIT_MODE_COMPATIBILITY_INSPECTION_IDS,
    inspectedFileCount = result.inspectedFileCount,
    issues = result.issues.sortedWith(compareBy(
      { it.filePath },
      { it.line ?: Int.MAX_VALUE },
      { it.column ?: Int.MAX_VALUE },
      { it.inspectionId },
      { it.description },
    )),
    timedOut = timedOut,
  )
}

private fun getSplitModeCompatibilityInspectionTools(project: Project): List<LocalInspectionToolWrapper> {
  return SPLIT_MODE_COMPATIBILITY_INSPECTION_IDS.map { inspectionId ->
    val wrapper = LocalInspectionToolWrapper.findTool2RunInBatch(project, null, inspectionId)
                  ?: mcpFail("Inspection not found: $inspectionId")
    val localWrapper = wrapper as? LocalInspectionToolWrapper
                       ?: mcpFail("Inspection is not a local inspection: $inspectionId")
    localWrapper.createCopy()
  }
}

private fun collectInspectionFiles(root: VirtualFile, project: Project): List<VirtualFile> {
  val projectFileIndex = ProjectFileIndex.getInstance(project)
  val files = mutableListOf<VirtualFile>()
  VfsUtilCore.visitChildrenRecursively(root, object : VirtualFileVisitor<Unit>() {
    override fun visitFile(file: VirtualFile): Boolean {
      ProgressManager.checkCanceled()
      if (projectFileIndex.isUnderIgnored(file)) {
        return false
      }
      if (file.isDirectory) {
        return true
      }

      val extension = file.extension?.lowercase()
      if (extension in SPLIT_MODE_COMPATIBILITY_FILE_EXTENSIONS && projectFileIndex.getModuleForFile(file, true) != null) {
        files += file
      }
      return true
    }
  })
  return files
}

private fun inspectSplitModeCompatibilityFile(
  project: Project,
  psiFile: PsiFile,
  tools: List<LocalInspectionToolWrapper>,
): List<DevKitMcpToolset.SplitModeCompatibilityIssue> {
  val descriptorsByTool = InspectionEngine.inspectEx(
    tools,
    psiFile,
    psiFile.textRange,
    psiFile.textRange,
    false,
    true,
    true,
    EmptyProgressIndicator(),
    PairProcessor.alwaysTrue(),
  )

  return descriptorsByTool.flatMap { (tool, descriptors) ->
    descriptors.map { descriptor ->
      descriptor.toSplitModeCompatibilityIssue(project, tool.shortName)
    }
  }
}

private fun ProblemDescriptor.toSplitModeCompatibilityIssue(
  project: Project,
  inspectionId: String,
): DevKitMcpToolset.SplitModeCompatibilityIssue {
  val textRange = (this as? ProblemDescriptorBase)?.textRange ?: psiElement?.textRange
  val virtualFile = (this as? ProblemDescriptorBase)?.containingFile ?: psiElement?.containingFile?.virtualFile
  val position = computePosition(virtualFile, textRange)
  return DevKitMcpToolset.SplitModeCompatibilityIssue(
    inspectionId = inspectionId,
    severity = highlightType.name,
    filePath = virtualFile?.toProjectRelativePath(project) ?: "<unknown>",
    line = position?.line,
    column = position?.column,
    endLine = position?.endLine,
    endColumn = position?.endColumn,
    description = ProblemDescriptorUtil.renderDescriptionMessage(this, psiElement, false),
    quickFixes = fixes?.map { it.familyName }?.distinct().orEmpty(),
  )
}

private fun computePosition(
  virtualFile: VirtualFile?,
  textRange: TextRange?,
): SourcePosition? {
  if (virtualFile == null || textRange == null) {
    return null
  }
  val document = FileDocumentManager.getInstance().getDocument(virtualFile) ?: return null
  val startOffset = textRange.startOffset.coerceIn(0, document.textLength)
  val endOffset = textRange.endOffset.coerceIn(startOffset, document.textLength)
  return SourcePosition(
    line = document.getLineNumber(startOffset) + 1,
    column = startOffset - document.getLineStartOffset(document.getLineNumber(startOffset)) + 1,
    endLine = document.getLineNumber(endOffset) + 1,
    endColumn = endOffset - document.getLineStartOffset(document.getLineNumber(endOffset)) + 1,
  )
}

private fun VirtualFile.toProjectRelativePath(project: Project): String {
  val basePath = project.basePath ?: return path
  val relativePath = path.removePrefix(basePath).removePrefix("/")
  return relativePath.ifEmpty { path }
}

private data class SourcePosition(
  val line: Int,
  val column: Int,
  val endLine: Int,
  val endColumn: Int,
)

private data class SplitModeCompatibilityInspectionRunResult(
  val issues: List<DevKitMcpToolset.SplitModeCompatibilityIssue>,
  val inspectedFileCount: Int,
)
