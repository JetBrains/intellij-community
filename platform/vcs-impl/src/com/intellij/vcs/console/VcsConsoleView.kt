// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.vcs.console

import com.intellij.execution.impl.ConsoleViewImpl
import com.intellij.execution.impl.EditorHyperlinkSupport
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange

class VcsConsoleView(project: Project) : ConsoleViewImpl(project, true) {
  companion object {
    private val LOG = logger<VcsConsoleView>()
  }

  override fun updateFoldings(startLine: Int, endLine: Int) {
    super.updateFoldings(startLine, endLine)

    editor.foldingModel.runBatchFoldingOperation {
      val document = editor.document
      for (line in startLine..endLine) {
        val oStart = document.getLineStartOffset(line)
        val oEnd = document.getLineEndOffset(line)
        val lineText = EditorHyperlinkSupport.getLineText(document, line, false)

        for (provider in VcsConsoleFolding.EP_NAME.extensions) {
          for (folding in provider.getFoldingsForLine(project, lineText)) {
            val foldingRange = folding.textRange.shiftRight(oStart)
            if (foldingRange.endOffset > oEnd) {
              LOG.error("Folding exceeds line length: $folding, provider: $provider")
              continue
            }

            val region = editor.foldingModel.addFoldRegion(foldingRange.startOffset, foldingRange.endOffset, folding.placeholder)
            region?.isExpanded = false
          }
        }
      }
    }
  }
}

interface VcsConsoleFolding {
  companion object {
    val EP_NAME: ExtensionPointName<VcsConsoleFolding> = ExtensionPointName("com.intellij.vcs.consoleFolding")
  }

  fun getFoldingsForLine(project: Project, line: String): List<Placeholder>

  data class Placeholder(val placeholder: String, val textRange: TextRange)
}