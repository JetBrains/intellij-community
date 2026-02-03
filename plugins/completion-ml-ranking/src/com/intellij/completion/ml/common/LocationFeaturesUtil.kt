// Copyright 2000-2022 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.completion.ml.common

import com.intellij.codeInsight.completion.CompletionParameters
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.psi.PsiElement

object LocationFeaturesUtil {
  private val LOG = Logger.getInstance(LocationFeaturesUtil.javaClass)

  fun indentLevel(line: String, tabSize: Int): Int {
    if (tabSize <= 0) return 0

    var indentLevel = 0
    var spaces = 0
    for (ch in line) {
      if (spaces == tabSize) {
        indentLevel += 1
        spaces = 0
      }

      if (ch == '\t') {
        indentLevel += 1
        spaces = 0
      }
      else if (ch == ' ') {
        spaces += 1
      }
      else {
        break
      }
    }

    return indentLevel
  }

  fun linesDiff(completionParameters: CompletionParameters, completionElement: PsiElement?): Int? {
    if (completionElement == null) {
      return null
    }

    try {
      val elementOffset = when (completionElement.containingFile) {
        completionParameters.position.containingFile -> {
          if (completionElement.textOffset >= completionParameters.position.textOffset) {
            val completionDiff = completionElement.containingFile.textLength - completionParameters.originalFile.textLength
            completionElement.textOffset - completionDiff
          } else {
            completionElement.textOffset
          }
        }
        completionParameters.originalFile -> completionElement.textOffset
        else -> null
      }
      if (elementOffset == null || elementOffset < 0) {
        return null
      }
      val completionLine = completionParameters.editor.caretModel.primaryCaret.logicalPosition.line
      val elementLine = completionParameters.editor.document.getLineNumber(elementOffset)
      return completionLine - elementLine
    } catch (e: ProcessCanceledException) {
      throw e
    } catch (e: Throwable) {
      LOG.error("Error while calculating lines diff", e)
      return null
    }
  }
}