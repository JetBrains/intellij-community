package org.jetbrains.completion.full.line.java.supporters

import com.intellij.codeInsight.daemon.impl.quickfix.ImportClassFix
import com.intellij.codeInsight.template.Template
import com.intellij.ide.highlighter.JavaFileType
import com.intellij.lang.java.JavaLanguage
import com.intellij.openapi.components.ServiceManager
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.fileTypes.FileType
import com.intellij.openapi.util.TextRange
import com.intellij.psi.*
import com.intellij.refactoring.suggested.endOffset
import com.intellij.refactoring.suggested.startOffset
import org.jetbrains.completion.full.line.java.JavaIconSet
import org.jetbrains.completion.full.line.language.LangState
import org.jetbrains.completion.full.line.language.ModelState
import org.jetbrains.completion.full.line.java.formatters.JavaCodeFormatter
import org.jetbrains.completion.full.line.java.formatters.JavaPsiCodeFormatter
import org.jetbrains.completion.full.line.language.supporters.FullLineLanguageSupporterBase

class JavaSupporter : FullLineLanguageSupporterBase() {
  override val fileType: FileType = JavaFileType.INSTANCE

  override val language = JavaLanguage.INSTANCE

  override val iconSet = JavaIconSet()

  override val formatter = JavaCodeFormatter()

  override val psiFormatter = JavaPsiCodeFormatter()

  override val langState: LangState = LangState(
    enabled = false,
    localModelState = ModelState(numIterations = 8),
  )

  override fun autoImportFix(file: PsiFile, editor: Editor, suggestionRange: TextRange): List<PsiElement> {
    return SyntaxTraverser.psiTraverser()
      .withRoot(file)
      .filter { it.reference is PsiJavaCodeReferenceElement && suggestionRange.contains(it.startOffset) }
      .toList()
      .distinctBy { it.text }
      .mapNotNull {
        val fix = ImportClassFix(it.reference as PsiJavaCodeReferenceElement)

        if (fix.isAvailable(it.project, editor, file)) {
          fix.invoke(it.project, editor, file)
          it
        }
        else {
          null
        }
      }
  }

  override fun createStringTemplate(element: PsiElement, range: TextRange): Template? {
    var content = range.substring(element.text)
    var contentOffset = 0
    return SyntaxTraverser.psiTraverser()
      .withRoot(element)
      .onRange(range)
      .filter { it is PsiJavaToken && (it.tokenType == JavaTokenType.STRING_LITERAL) }
      .asIterable()
      .mapIndexedNotNull { id, it ->
        if (it is PsiJavaToken) {
          val stringContentRange = TextRange(it.startOffset + 1, it.endOffset - 1)
            .shiftRight(contentOffset - range.startOffset)
          val name = "\$__Variable${id}\$"
          val stringContent = stringContentRange.substring(content)

          contentOffset += name.length - stringContentRange.length

          content = stringContentRange.replace(content, name)
          stringContent
        }
        else {
          null
        }
      }.let {
        createTemplate(content, it)
      }
  }

  override fun createCodeFragment(file: PsiFile, text: String, isPhysical: Boolean): PsiFile {
    return ServiceManager.getService(file.project, JavaCodeFragmentFactory::class.java)
      .createCodeBlockCodeFragment(text, file, isPhysical)
  }

  override fun containsReference(element: PsiElement, range: TextRange): Boolean {
    if (element is PsiExpressionStatement) {
      return element.expression
        .let { if (it is PsiParenthesizedExpression) it.expression != null else true }
    }

    // Find all references and psi errors
    return (element is PsiReference
            || element is PsiIdentifier
            || element is PsiStatement
            || element is PsiAnnotationOwner
            || element is PsiNamedElement)
           // Check if elements does not go beyond the boundaries
           && range.contains(element.textRange)
  }


  override fun isStringElement(element: PsiElement): Boolean {
    return element is PsiJavaToken && (element.tokenType == JavaTokenType.STRING_LITERAL)
  }

  override fun isStringWalkingEnabled(element: PsiElement): Boolean {
    return true
  }
}
