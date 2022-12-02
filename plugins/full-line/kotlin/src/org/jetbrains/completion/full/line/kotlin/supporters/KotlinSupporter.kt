package org.jetbrains.completion.full.line.kotlin.supporters

import com.intellij.codeInsight.completion.CompletionParameters
import com.intellij.codeInsight.template.Template
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.fileTypes.FileType
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.SyntaxTraverser
import com.intellij.refactoring.suggested.endOffset
import com.intellij.refactoring.suggested.startOffset
import org.jetbrains.completion.full.line.kotlin.KotlinIconSet
import org.jetbrains.completion.full.line.language.LangState
import org.jetbrains.completion.full.line.language.RedCodePolicy
import org.jetbrains.completion.full.line.language.formatters.DummyPsiCodeFormatter
import org.jetbrains.completion.full.line.kotlin.formatters.KotlinCodeFormatter
import org.jetbrains.completion.full.line.language.supporters.FullLineLanguageSupporterBase
import org.jetbrains.kotlin.idea.KotlinFileType
import org.jetbrains.kotlin.idea.KotlinLanguage
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.*

class KotlinSupporter : FullLineLanguageSupporterBase() {
  override val fileType: FileType = KotlinFileType.INSTANCE as FileType

  override val language = KotlinLanguage.INSTANCE

  override val iconSet = KotlinIconSet()

  override val formatter = KotlinCodeFormatter()

  override val psiFormatter = DummyPsiCodeFormatter()

  override val langState: LangState = LangState(
    enabled = false,
    redCodePolicy = RedCodePolicy.SHOW,
  )
  override val modelVersion = "0.0.4"

  override fun autoImportFix(file: PsiFile, editor: Editor, suggestionRange: TextRange): List<PsiElement> {
    //        TODO("Not implemented yet")
    return emptyList()
  }

  override fun skipLocation(parameters: CompletionParameters): String? {
    val file = parameters.originalFile
    val ktsSkip = if (file is KtFile && file.isScript()) "Disabled in gradle kotlin dsl scripts" else null

    return super.skipLocation(parameters) ?: ktsSkip
  }

  override fun createStringTemplate(element: PsiElement, range: TextRange): Template? {
    var content = range.substring(element.text)
    var contentOffset = 0
    return SyntaxTraverser.psiTraverser()
      .withRoot(element)
      .onRange(range)
      .filter { it is KtStringTemplateExpression }
      .asIterable()
      .mapIndexedNotNull { id, it ->
        val startQuote = it.node.firstChildNode
          .let { if (it.elementType == KtTokens.OPEN_QUOTE) it.textLength else 0 }
        val endQuote = it.node.lastChildNode
          .let { if (it.elementType == KtTokens.CLOSING_QUOTE) it.textLength else 0 }

        val stringContentRange = TextRange(it.startOffset + startQuote, it.endOffset - endQuote)
          .shiftRight(contentOffset - range.startOffset)
        val name = "\$__Variable${id}\$"
        val stringContent = stringContentRange.substring(content)

        contentOffset += name.length - stringContentRange.length

        content = stringContentRange.replace(content, name)
        stringContent
      }.let {
        createTemplate(content, it)
      }
  }

  override fun createCodeFragment(file: PsiFile, text: String, isPhysical: Boolean): PsiFile {
    // TODO support isPhysical
    return KtBlockCodeFragment(file.project, file.name, text, null, file)
  }

  override fun containsReference(element: PsiElement, range: TextRange): Boolean {
    // Find all references and psi errors
    return element is KtExpression
           // Skip checking constants (numbers, chars, etc)
           && element !is KtConstantExpression
           // Skip checking strings
           && element !is KtStringTemplateExpression
           // Check if elements does not go beyond the boundaries
           && range.contains(element.textRange)
  }

  override fun isStringElement(element: PsiElement): Boolean {
    return element is KtStringTemplateEntry
  }

  override fun isStringWalkingEnabled(element: PsiElement): Boolean {
    if (element is KtStringTemplateExpression) {
      return !element.text.startsWith("\"\"\"")
    }
    return true
  }
}
