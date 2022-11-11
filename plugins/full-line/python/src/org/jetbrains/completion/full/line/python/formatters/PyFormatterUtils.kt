package org.jetbrains.completion.full.line.python.formatters

import com.intellij.psi.PsiElement
import com.intellij.psi.PsiWhiteSpace
import com.intellij.psi.util.nextLeafs
import com.intellij.psi.util.prevLeafs
import com.jetbrains.python.psi.PyExpression
import org.jetbrains.completion.full.line.language.formatters.CodeFormatterBase
import org.jetbrains.completion.full.line.python.formatters.elements.WhitespaceFormatter.Companion.decreaseIntendLevel
import org.jetbrains.completion.full.line.python.formatters.elements.WhitespaceFormatter.Companion.increaseIntendLevel

fun CodeFormatterBase.handlePyArgumentList(
  arguments: Array<PyExpression>,
  scopeInChar: Char, scopeOutChar: Char,
  lastComma: Boolean, closed: Boolean,
  argsAtNewLines: Boolean, wrapWithScope: Boolean,
): String {
  val file = arguments.first().containingFile
  if (wrapWithScope) increaseIntendLevel(file)

  return handlePyArgumentListInternal(
    arguments.toList(),
    scopeInChar, scopeOutChar,
    lastComma, closed,
    argsAtNewLines, wrapWithScope,
  ) {
    formatAll(it, it.textRange)
  }.also {
    if (wrapWithScope) decreaseIntendLevel(file)
  }
}

fun <T> handlePyArgumentList(
  arguments: List<T>,
  scopeInChar: Char, scopeOutChar: Char,
  lastComma: Boolean, closed: Boolean,
  argsAtNewLines: Boolean, wrapWithScope: Boolean,
  elementToSting: (T) -> String,
): String {
  return handlePyArgumentListInternal(
    arguments,
    scopeInChar, scopeOutChar,
    lastComma, closed,
    argsAtNewLines, wrapWithScope,
    elementToSting,
  )
}

fun <T> handlePyArgumentListInternal(
  arguments: List<T>,
  scopeInChar: Char, scopeOutChar: Char,
  lastComma: Boolean, closed: Boolean,
  argsAtNewLines: Boolean, wrapWithScope: Boolean,
  elementToSting: (T) -> String,
): String {
  if (arguments.isEmpty()) return "()"
  val addIntend = wrapWithScope || argsAtNewLines

  val postfixComma = if (lastComma) "," else ""
  val postfixBracket = if (closed) ")" else ""
  val postfixScope = if (addIntend) scopeOutChar else ""
  val postfix = postfixComma + postfixScope + postfixBracket


  return arguments.joinToString(
    if (argsAtNewLines) ",\n" else ", ",
    if (addIntend) "($scopeInChar" else "(",
    postfix
  ) {
    elementToSting(it)
  }
}

fun PsiElement.intend(): Int {
  return prevLeafs.find { it is PsiWhiteSpace && it.textContains('\n') }
           ?.text?.takeLastWhile { it != '\n' }?.length ?: 0
}

fun PsiElement.firstPrevLeafWithText(text: String): PsiElement? {
  return prevLeafs.first { it !is PsiWhiteSpace }.takeIf { it.text == text }
}

fun PsiElement.firstNextLeafWithText(text: String): PsiElement? {
  return nextLeafs.first { it !is PsiWhiteSpace }.takeIf { it.text == text }
}
