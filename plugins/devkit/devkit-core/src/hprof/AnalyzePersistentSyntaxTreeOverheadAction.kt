// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.hprof

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.EDT
import com.intellij.openapi.components.Service
import com.intellij.openapi.fileChooser.FileChooser
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileTypes.PlainTextFileType
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.vfs.toNioPathOrNull
import com.intellij.testFramework.LightVirtualFile
import com.intellij.util.PathUtil
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.idea.devkit.DevKitBundle
import java.nio.file.Path
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

private val REPORT_TIMESTAMP_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")
private val BYTE_UNITS: Array<String> = arrayOf("B", "KiB", "MiB", "GiB", "TiB")

internal class AnalyzePersistentSyntaxTreeOverheadAction : DumbAwareAction() {
  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

  override fun update(e: AnActionEvent) {
    e.presentation.isEnabled = e.project != null
  }

  override fun actionPerformed(e: AnActionEvent) {
    val project = e.project ?: return
    val hprofFile = FileChooser.chooseFile(
      FileChooserDescriptorFactory.singleFile()
        .withTitle(DevKitBundle.message("persistent.syntax.tree.hprof.action.chooser.title"))
        .withDescription(DevKitBundle.message("persistent.syntax.tree.hprof.action.chooser.description"))
        .withExtensionFilter(DevKitBundle.message("persistent.syntax.tree.hprof.action.chooser.filter"), "hprof"),
      project,
      null,
    ) ?: return
    val hprofPath = hprofFile.toNioPathOrNull()
    if (hprofPath == null) {
      Messages.showErrorDialog(
        project,
        DevKitBundle.message("persistent.syntax.tree.hprof.action.error.local.file.required", hprofFile.presentableUrl),
        DevKitBundle.message("persistent.syntax.tree.hprof.action.error.title"),
      )
      return
    }

    e.coroutineScope.launch(Dispatchers.IO) {
      try {
        val analysis = PersistentSyntaxTreeHprofProcessor.analyzePersistentSyntaxTreeOverheadWithProgress(project, hprofPath)
        val reportText = formatReport(hprofPath, analysis)
        val reportFileName = reportFileName(hprofPath)
        withContext(Dispatchers.EDT) {
          FileEditorManager.getInstance(project).openFile(LightVirtualFile(reportFileName, PlainTextFileType.INSTANCE, reportText), true)
        }
      }
      catch (e: CancellationException) {
        throw e
      }
      catch (e: ProcessCanceledException) {
        throw e
      }
      catch (error: Throwable) {
        withContext(Dispatchers.EDT) {
          Messages.showErrorDialog(
            project,
            DevKitBundle.message("persistent.syntax.tree.hprof.action.error.analysis.failed", error.message ?: error.javaClass.name),
            DevKitBundle.message("persistent.syntax.tree.hprof.action.error.title"),
          )
        }
      }
    }
  }

  private fun formatReport(hprofPath: Path, analysis: PersistentSyntaxTreeOverheadAnalysis): String {
    val extraction = analysis.extraction
    return buildString {
      appendLine("Persistent syntax tree overhead")
      appendLine()
      appendLine("Heap dump: $hprofPath")
      appendLine("VersionedPayloadMap instances: ${extraction.allInstances.size}")
      appendLine("  VersionedPayloadMap1: ${extraction.map1Instances.size}")
      appendLine("  VersionedPayloadMap2: ${extraction.map2Instances.size}")
      appendLine("  ArrayVersionedPayloadMap: ${extraction.arrayMapInstances.size}")
      appendLine()
      appendLine("Retained stale-version overhead: ${formatByteSize(analysis.retainedOverheadBytes)} (${analysis.retainedOverheadBytes} bytes)")
      appendLine("Retained objects: ${analysis.retainedObjectCount}")
      appendLine("Stale roots: ${analysis.staleRootCount}")
      appendLine("Live roots: ${analysis.liveRootCount}")
      appendLine("Stale-reachable objects: ${analysis.staleReachableObjectCount}")
      appendLine("Live-reachable objects: ${analysis.liveReachableObjectCount}")
      appendLine()
      appendLine("Stale-only nested VersionedPayloadMap objects: ${analysis.staleOnlyMapObjectIds.size}")
      if (analysis.staleOnlyMapObjectIds.isNotEmpty()) {
        appendLine(formatObjectIds(analysis.staleOnlyMapObjectIds))
      }
      appendLine()
      appendLine("Retained objects by class")
      val bytesWidth = maxOf("Bytes".length, analysis.retainedObjectsByClass.maxOfOrNull { it.retainedBytes.toString().length } ?: 0)
      val objectsWidth = maxOf("Objects".length, analysis.retainedObjectsByClass.maxOfOrNull { it.retainedObjectCount.toString().length } ?: 0)
      val humanSizeWidth = maxOf("Human size".length, analysis.retainedObjectsByClass.maxOfOrNull { formatByteSize(it.retainedBytes).length } ?: 0)
      appendLine("${"Bytes".padStart(bytesWidth)}  ${"Objects".padStart(objectsWidth)}  ${"Human size".padStart(humanSizeWidth)}  Class")
      for ((className, retainedObjectCount, retainedBytes) in analysis.retainedObjectsByClass) {
        val humanSize = formatByteSize(retainedBytes)
        appendLine(
          "${retainedBytes.toString().padStart(bytesWidth)}  " +
          "${retainedObjectCount.toString().padStart(objectsWidth)}  " +
          "${humanSize.padStart(humanSizeWidth)}  " +
          className
        )
      }
    }
  }

  private fun reportFileName(hprofPath: Path): String {
    val hprofName = hprofPath.fileName?.toString()?.removeSuffix(".hprof")?.removeSuffix(".HPROF") ?: "heap-dump"
    val sourceName = PathUtil.suggestFileName(hprofName).ifEmpty { "heap-dump" }
    val timestamp = REPORT_TIMESTAMP_FORMATTER.format(ZonedDateTime.now())
    return "persistent-syntax-tree-overhead-$sourceName-$timestamp.txt"
  }

  private fun formatObjectIds(objectIds: Set<Long>): String {
    val ids = objectIds.sorted().take(MAX_REPORTED_OBJECT_IDS).joinToString(", ") { objectId -> "0x${objectId.toString(16)}" }
    return if (objectIds.size <= MAX_REPORTED_OBJECT_IDS) ids else "$ids, ..."
  }

  private fun formatByteSize(bytes: Long): String {
    if (bytes < 1024) {
      return "$bytes B"
    }
    var value = bytes.toDouble()
    var unitIndex = 0
    while (value >= 1024 && unitIndex < BYTE_UNITS.lastIndex) {
      value /= 1024
      unitIndex++
    }
    return "%.1f %s".format(Locale.US, value, BYTE_UNITS[unitIndex])
  }

  private companion object {
    const val MAX_REPORTED_OBJECT_IDS: Int = 100
  }
}

@Service(Service.Level.PROJECT)
private class AnalyzePersistentSyntaxTreeOverheadCoroutineScopeProvider(val coroutineScope: CoroutineScope)
