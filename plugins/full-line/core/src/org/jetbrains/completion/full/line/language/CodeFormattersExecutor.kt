package org.jetbrains.completion.full.line.language

import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.SyntaxTraverser
import com.intellij.refactoring.suggested.startOffset
import org.jetbrains.completion.full.line.language.formatters.SkippedElementsFormatter

class CodeFormattersExecutor {
  lateinit var formatters: List<ElementFormatter>
}

