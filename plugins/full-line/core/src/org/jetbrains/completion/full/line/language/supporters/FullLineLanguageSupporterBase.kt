package org.jetbrains.completion.full.line.language.supporters

import com.intellij.codeInsight.completion.CompletionParameters
import com.intellij.codeInsight.template.Template
import com.intellij.codeInsight.template.impl.TemplateImpl
import com.intellij.codeInsight.template.impl.TextExpression
import com.intellij.model.psi.impl.targetSymbols
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.fileTypes.FileType
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiComment
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.SyntaxTraverser
import com.intellij.refactoring.suggested.endOffset
import com.intellij.refactoring.suggested.startOffset
import org.jetbrains.completion.full.line.ReferenceCorrectness
import org.jetbrains.completion.full.line.language.FullLineConfiguration
import org.jetbrains.completion.full.line.language.FullLineLanguageSupporter
import org.jetbrains.completion.full.line.language.LocationMatcher
import java.util.*

abstract class FullLineLanguageSupporterBase(extraMatchers: List<LocationMatcher> = emptyList()) : FullLineLanguageSupporter {
  @Suppress("PropertyName", "MemberVisibilityCanBePrivate")
  protected val LOG = Logger.getInstance(this::class.java)

  private val matchers: List<LocationMatcher> = extraMatchers + defaultMatchers()

  abstract val fileType: FileType

  override fun skipLocation(parameters: CompletionParameters): String? {
    if (inComment(parameters.position)) {
      return "Full line unavailable in comments"
    }

    return null
  }

  final override fun configure(parameters: CompletionParameters): FullLineConfiguration {
    for (matcher in matchers) {
      val config = matcher.tryMatch(parameters)
      if (config != null) return config
    }

    return FullLineConfiguration.oneToken(this)
  }

  override fun getFirstToken(line: String): String? {
    var curToken = ""
    var offset = 0
    for (ch in line) {
      if (ch == '_' || ch.isLetter() || ch.isDigit()) {
        curToken += ch
      }
      else if (curToken.isNotEmpty()) {
        return line.substring(0, curToken.length)
      }
      else {
        return null
      }
      offset++
    }
    if (curToken.isNotEmpty())
      return line.substring(0, curToken.length)
    return null
  }

  override fun getMissingBraces(line: String, element: PsiElement?, offset: Int): List<Char>? {
    val stack = Stack<Char>()
    val charArray = line.toCharArray()

    var isStringQuotedUsed: Boolean? = null
    var lastStringMarker: Char = element?.let {
      val inString = isStringElement(element)

      // Add leading quote to stack only if completion called inside of string, or at the end of unclosed string
      if (inString
          && element.startOffset != offset
          && !(element.endOffset == offset && STRING_MARKERS.contains(element.text.last()))
      ) {
        isStringQuotedUsed = true
        element.text.first().also {
          stack.push(it)
        }
      }
      else {
        null
      }
    } ?: Char.MIN_VALUE

    charArray.forEachIndexed { index, c ->
      if (c in STRING_MARKERS && (index == 0 || (charArray[index - 1] != ESCAPE_SYMBOL))) {
        // if we got here, we probably are on the edge of a string literal
        if (lastStringMarker == Char.MIN_VALUE) {
          // we definitely are in the beginning of the string literal
          // seize the marker, suspend stack operations
          lastStringMarker = c
          stack.push(c)
        }
        else if (CLOSE_TO_OPEN[c]?.equals(lastStringMarker)!!) {
          // the symbol is correct, we definitely are in the end of the string literal
          // release the marker, resume stack operations
          if (isStringQuotedUsed == true) {
            isStringQuotedUsed = false
          }
          lastStringMarker = Char.MIN_VALUE
          stack.pop()
        }
        // else we are in a string literal, do nothing
      }
      else if (lastStringMarker == Char.MIN_VALUE) {
        // we are neither in a string literal nor on the edge of it
        // we can perform stack operations
        if (OPENERS.contains(c)) {
          stack.push(c)
        }
        else if (CLOSE_TO_OPEN.containsKey(c)) {
          try {
            if (isStringQuotedUsed == true) {
              isStringQuotedUsed = false
            }
            val opener = stack.pop()
            if (!CLOSE_TO_OPEN[c]?.equals(opener)!!) {
              return null
            }
          }
          catch (ignore: EmptyStackException) {
            return null
          }
        }
      }
      // else we are in a string literal, do nothing
    }
    if (isStringQuotedUsed == true) {
      stack.pop()
    }

    if (!stack.isEmpty()) {
      return stack.reversed().map { OPEN_TO_CLOSE[it]!! }
    }
    return null
  }

  @Suppress("UnstableApiUsage")
  override fun isCorrect(file: PsiFile, suggestion: String, offset: Int, prefix: String): ReferenceCorrectness {
    val startPsiBuild = System.currentTimeMillis()
    val psi = createCodeFragment(file,
                                 file.text.let { it.take(offset - prefix.length) + suggestion + it.drop(offset) }
    ) ?: return ReferenceCorrectness.UNDEFINED

    val psiWithPar: PsiFile? by lazy {
      createCodeFragment(file,
                         file.text.let { it.take(offset - prefix.length) + "$suggestion()" + it.drop(offset) }
      )
    }
    LOG.debug("Psi build for ${System.currentTimeMillis() - startPsiBuild}ms.")

    val range = TextRange(offset - prefix.length, offset + suggestion.length - prefix.length)

    // Check that the suggestion will not cause any errors
    val startTraverse = System.currentTimeMillis()
    return SyntaxTraverser.psiTraverser()
      .withRoot(psi)
      .onRange(range)
      .filter { containsReference(it, range) }
      .all {
        val startElementCheck = System.currentTimeMillis()
        val res = isElementCorrect(it, psi)
                    .takeIf { it }
                  ?: psiWithPar?.let { file -> isElementCorrect(it, file) }
                  ?: false
        val end = System.currentTimeMillis() - startElementCheck
        LOG.debug("${it::class.java}:${it.text} correctness is $res. Took ${end}ms to compute")
        res
      }
      .let { if (it) ReferenceCorrectness.CORRECT else ReferenceCorrectness.INCORRECT }
      .also { LOG.debug("Traverse finished for ${System.currentTimeMillis() - startTraverse}ms.") }
  }

  @Suppress("UnstableApiUsage")
  protected open fun isElementCorrect(element: PsiElement, file: PsiFile): Boolean {
    return targetSymbols(file, element.textOffset).isNotEmpty()
      .also { ProgressManager.checkCanceled() }
  }

  abstract fun containsReference(element: PsiElement, range: TextRange): Boolean

  protected fun createTemplate(content: String, variables: List<String>): Template? {
    return if (variables.isEmpty()) {
      null
    }
    else {
      TemplateImpl("fakeKey", content, "ml server completion").apply {
        variables.forEach { variable -> addVariable(TextExpression(variable), true) }
      }
    }
  }

  private fun inComment(element: PsiElement): Boolean = element is PsiComment

  companion object {
    const val ESCAPE_SYMBOL = '\\'
    val STRING_MARKERS = arrayListOf('"', '\'')

    var CLOSE_TO_OPEN = hashMapOf(
      // TODO: Bring back handling such angle brackets when element will be extended with psi.
      // '>' to '<',
      ')' to '(',
      ']' to '[',
      '}' to '{',
      '"' to '"',
      '\'' to '\'',
    )

    var OPEN_TO_CLOSE = CLOSE_TO_OPEN.entries.associate { (k, v) -> v to k }

    var OPENERS = OPEN_TO_CLOSE.keys

    private fun defaultMatchers(): List<LocationMatcher> {
      return listOf(EndOfLineMatcher)
    }
  }

  private object EndOfLineMatcher : LocationMatcher {
    override fun tryMatch(parameters: CompletionParameters): FullLineConfiguration? {
      val document = parameters.editor.document
      val lineEndOffset = document.getLineEndOffset(document.getLineNumber(parameters.offset))

      if (document.getText(TextRange.create(parameters.offset, lineEndOffset)).isBlank()) {
        return FullLineConfiguration.Line
      }

      return null
    }
  }
}
