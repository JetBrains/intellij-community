// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.performance

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.actionSystem.LatencyRecorder
import com.intellij.openapi.editor.impl.EditorImpl
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileTypes.FileType
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.ui.ColoredTreeCellRenderer
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.treeStructure.Tree
import com.intellij.util.ui.tree.TreeUtil
import javax.swing.Action
import javax.swing.JComponent
import javax.swing.JTree
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.DefaultTreeModel

/**
 * @author yole
 */

class LatencyRecorderImpl : LatencyRecorder {
  override fun recordLatencyAwareAction(editor: Editor, actionId: String, event: AnActionEvent) {
    (editor as? EditorImpl)?.recordLatencyAwareAction(actionId, event)
  }
}

class LatencyRecord {
  var totalKeysTyped = 0
  var totalLatency = 0L
  var maxLatency = 0L

  fun update(latencyInMS: Long) {
    totalKeysTyped++
    totalLatency += latencyInMS
    if (latencyInMS > maxLatency) {
      maxLatency = latencyInMS
    }
  }

  val averageLatency get() = totalLatency / totalKeysTyped
}

class FileTypeLatencyRecord(val fileType: FileType) {
  val totalLatency = LatencyRecord()
  val actionLatencyRecords = mutableMapOf<String, LatencyRecord>()

  fun update(action: String, latencyInMS: Long) {
    totalLatency.update(latencyInMS)
    actionLatencyRecords.getOrPut(action) { LatencyRecord() }.update(latencyInMS)
  }
}

val latencyMap = mutableMapOf<FileType, FileTypeLatencyRecord>()

fun recordTypingLatency(editor: Editor, action: String, latencyInMS: Long) {
  val fileType = FileDocumentManager.getInstance().getFile(editor.document)?.fileType ?: return
  val latencyRecord = latencyMap.getOrPut(fileType) {
    FileTypeLatencyRecord(fileType)
  }
  latencyRecord.update(getActionKey(action), latencyInMS)
}

fun getActionKey(action: String) =
  if (action.length == 1) {
    when(action[0]) {
      in 'A'..'Z', in 'a'..'z', in '0'..'9' -> "Letter"
      ' ' -> "Space"
      else -> action
    }
  }
  else action

class TypingLatencyReportAction : AnAction() {
  override fun actionPerformed(e: AnActionEvent) {
    val project = e.project ?: return
    TypingLatencyReportDialog(project).show()
  }
}

class TypingLatencyReportDialog(project: Project) : DialogWrapper(project) {
  init {
    init()
    title = "Typing Latency Report"
  }

  override fun createCenterPanel(): JComponent {
    val root = DefaultMutableTreeNode()
    for (row in latencyMap.values.sortedBy { it.fileType.name }) {
      val rowNode = DefaultMutableTreeNode(row)
      root.add(rowNode)
      for (actionLatencyRecord in row.actionLatencyRecords.entries.sortedByDescending { it.value.averageLatency }) {
        rowNode.add(DefaultMutableTreeNode(actionLatencyRecord.toPair()))
      }
    }
    val reportList = Tree(DefaultTreeModel(root))
    reportList.isRootVisible = false
    reportList.cellRenderer = object : ColoredTreeCellRenderer() {
      override fun customizeCellRenderer(tree: JTree,
                                         value: Any?,
                                         selected: Boolean,
                                         expanded: Boolean,
                                         leaf: Boolean,
                                         row: Int,
                                         hasFocus: Boolean) {
        if (value == null) return
        val obj = (value as DefaultMutableTreeNode).userObject
        if (obj is FileTypeLatencyRecord) {
          append(obj.fileType.name)
          icon = obj.fileType.icon
          appendLatencyRecord(obj.totalLatency)
        }
        else if (obj is Pair<*, *>) {
          val pair = obj as Pair<String, LatencyRecord>
          append(pair.first)
          appendLatencyRecord(pair.second)
        }
      }

      private fun appendLatencyRecord(latencyRecord: LatencyRecord) {
        append(" - avg ")
        append(latencyRecord.averageLatency.toString())
        append("ms, max ")
        append(latencyRecord.maxLatency.toString())
        append("ms")
      }
    }
    TreeUtil.expandAll(reportList)
    return JBScrollPane(reportList)
  }

  override fun createActions(): Array<Action> {
    return arrayOf(okAction)
  }
}
