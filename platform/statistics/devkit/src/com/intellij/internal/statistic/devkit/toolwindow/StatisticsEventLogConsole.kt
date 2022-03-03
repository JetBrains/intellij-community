// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal.statistic.devkit.toolwindow

import com.intellij.diagnostic.logging.LogConsoleBase
import com.intellij.diagnostic.logging.LogFilterModel
import com.intellij.internal.statistic.devkit.actions.OpenEventsSchemeFileAction.Companion.getEventsSchemeFile
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

internal class StatisticsEventLogConsole(private val project: Project, model: LogFilterModel, recorderId: String)
  : LogConsoleBase(project, null, eventLogToolWindowsId, false, model) {

  init {
    val schemeFile = LocalFileSystem.getInstance().findFileByNioFile(getEventsSchemeFile(recorderId))
    if (schemeFile != null) {
      val groupIdToLine = ReadAction.compute<HashMap<String, Int>, Throwable> {
        computeLineNumbers(schemeFile)
      }
      if (groupIdToLine != null && groupIdToLine.isNotEmpty()) {
        console?.addMessageFilter(StatisticsEventLogFilter(schemeFile, groupIdToLine))
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
}