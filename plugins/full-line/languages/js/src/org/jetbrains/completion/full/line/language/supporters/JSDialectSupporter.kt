package org.jetbrains.completion.full.line.language.supporters

import com.intellij.codeInsight.template.Template
import com.intellij.lang.javascript.JSTokenTypes
import com.intellij.lang.javascript.psi.JSReferenceExpression
import com.intellij.lang.javascript.psi.impl.JSExpressionCodeFragmentImpl
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.SyntaxTraverser
import com.intellij.psi.util.elementType
import com.intellij.refactoring.suggested.endOffset
import com.intellij.refactoring.suggested.startOffset

abstract class JSDialectSupporter : FullLineLanguageSupporterBase() {
    override fun createCodeFragment(file: PsiFile, text: String, isPhysical: Boolean): PsiFile? {
        return JSExpressionCodeFragmentImpl(file.project, file.name, text, isPhysical, null)
    }

    override fun containsReference(element: PsiElement, range: TextRange): Boolean {
        return range.contains(element.textRange) && element is JSReferenceExpression
    }

    // TODO("Not yet implemented")
    override fun autoImportFix(file: PsiFile, editor: Editor, suggestionRange: TextRange): List<PsiElement> {
        return emptyList()
    }

    override fun createStringTemplate(element: PsiElement, range: TextRange): Template? {
        var content = range.substring(element.text)
        var contentOffset = 0
        return SyntaxTraverser.psiTraverser()
            .withRoot(element)
            .onRange(range)
            .filter { isStringElement(it) }
            .asIterable()
            .mapIndexedNotNull { id, it ->
                val stringContentRange = TextRange(it.startOffset + 1, it.endOffset - 1)
                    .shiftRight(contentOffset - range.startOffset)
                val name = "\$__Variable${id}\$"
                val stringContent = stringContentRange.substring(content)

                contentOffset += name.length - stringContentRange.length

                content = stringContentRange.replace(content, name)
                stringContent
            }
            .let {
                createTemplate(content, it)
            }
    }

    override fun isStringElement(element: PsiElement): Boolean {
        return element.elementType == JSTokenTypes.STRING_LITERAL
    }

    override fun isStringWalkingEnabled(element: PsiElement): Boolean {
        return true
    }
}
