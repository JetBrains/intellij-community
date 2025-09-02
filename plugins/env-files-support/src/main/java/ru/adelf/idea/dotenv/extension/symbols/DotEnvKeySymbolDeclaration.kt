package ru.adelf.idea.dotenv.extension.symbols

import com.intellij.find.usages.api.PsiUsage
import com.intellij.model.Pointer
import com.intellij.model.Symbol
import com.intellij.model.psi.PsiSymbolDeclaration
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile

class DotEnvKeySymbolDeclaration(private val element: PsiElement, private val range: TextRange = element.textRange): PsiSymbolDeclaration {

    override fun getDeclaringElement(): PsiElement = element

    override fun getRangeInDeclaringElement(): TextRange = TextRange(0, range.length)

    override fun getSymbol(): Symbol = DotEnvKeySymbol(element.text, element.containingFile, range)

}

class DotEnvKeyDeclarationUsage(override val file: PsiFile, override val range: TextRange) : PsiUsage {

    override val declaration: Boolean
        get() = true

    override fun createPointer(): Pointer<out PsiUsage> = PsiUsage.textUsage(file, range).createPointer()

}
