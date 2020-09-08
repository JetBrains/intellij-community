// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistic.toolwindow

import com.intellij.diagnostic.logging.LogConsoleBase
import com.intellij.diagnostic.logging.LogFilterModel
import com.intellij.execution.filters.Filter
import com.intellij.execution.filters.OpenFileHyperlinkInfo
import com.intellij.internal.statistic.actions.OpenEventsSchemeFileAction.Companion.getEventsSchemeFile
import com.intellij.json.JsonLanguage
import com.intellij.json.psi.JsonArray
import com.intellij.json.psi.JsonObject
import com.intellij.json.psi.JsonProperty
import com.intellij.json.psi.JsonStringLiteral
import com.intellij.json.psi.impl.JsonRecursiveElementVisitor
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.psi.PsiManager
import com.intellij.refactoring.suggested.startOffset
import java.util.regex.Pattern

internal class StatisticsEventLogConsole(val project: Project, val model: LogFilterModel, recorderId: String)
  : LogConsoleBase(project, null, eventLogToolWindowsId, false, model) {

  init {
    val schemeFile = LocalFileSystem.getInstance().findFileByIoFile(getEventsSchemeFile(recorderId))
    if (schemeFile != null) {
      val groupIdToLine = ReadAction.compute<HashMap<String, Int>, Throwable> {
        computeLineNumbers(schemeFile)
      }
      if (groupIdToLine != null && groupIdToLine.isNotEmpty()) {
        console?.addMessageFilter(StatisticsLogFilter(project, schemeFile, groupIdToLine))
      }
    }
  }

  private fun computeLineNumbers(schemeFile: VirtualFile): HashMap<String, Int>? {
    val groupIdToLine = HashMap<String, Int>()
    val document = FileDocumentManager.getInstance().getDocument(schemeFile) ?: return null
    val psiFile = PsiManager.getInstance(project).findFile(schemeFile) ?: return null
    if (psiFile.language == JsonLanguage.INSTANCE) {
      psiFile.accept(object : JsonRecursiveElementVisitor() {
        override fun visitProperty(property: JsonProperty) {
          if (property.name != "groups") return
          val groups = property.value as? JsonArray ?: return
          for (groupElement in groups.valueList) {
            val groupObject = groupElement as JsonObject
            val idProperty = groupObject.findProperty("id") ?: continue
            val id = (idProperty.value as? JsonStringLiteral)?.value ?: continue
            groupIdToLine[id] = document.getLineNumber(idProperty.startOffset)
          }
        }
      })
    }
    return groupIdToLine
  }

  override fun isActive(): Boolean {
    return ToolWindowManager.getInstance(project).getToolWindow(eventLogToolWindowsId)?.isVisible ?: false
  }

  fun addLogLine(line: String) {
    super.addMessage(line)
  }

  class StatisticsLogFilter(val project: Project, val file: VirtualFile, val groupIdToLine: HashMap<String, Int>) : Filter {
    private val pattern = Pattern.compile("\\[\"(?<groupId>.*)\", v\\d+]")

    override fun applyFilter(line: String, entireLength: Int): Filter.Result? {
      val start = entireLength - line.length

      val matcher = pattern.matcher(line)
      if (!matcher.find()) return null

      val groupName = "groupId"
      val lineNumber = groupIdToLine[matcher.group(groupName)]
      if (lineNumber == null) return null
      return Filter.Result(matcher.start(groupName) + start, matcher.end(groupName) + start,
                           OpenFileHyperlinkInfo(project, file, lineNumber))
    }

  }
}