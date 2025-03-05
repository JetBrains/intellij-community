package ru.adelf.idea.dotenv.extension.symbols

import com.intellij.model.Symbol
import com.intellij.model.psi.PsiSymbolDeclaration
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import ru.adelf.idea.dotenv.psi.DotEnvKey

class DotEnvKeySymbolDeclaration(private val element: PsiElement, private val range: TextRange = element.textRange): PsiSymbolDeclaration {

    override fun getDeclaringElement(): PsiElement = element

    override fun getRangeInDeclaringElement(): TextRange = TextRange(0, range.length)

    override fun getSymbol(): Symbol = DotEnvKeySymbol(element.text, element.containingFile, range)

}