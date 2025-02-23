package ru.adelf.idea.dotenv.extension.symbols

import com.intellij.model.psi.PsiSymbolReference
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import org.jetbrains.annotations.Unmodifiable
import ru.adelf.idea.dotenv.api.EnvironmentVariablesApi
import ru.adelf.idea.dotenv.psi.DotEnvProperty

class DotEnvKeyReference(private val element: PsiElement): PsiSymbolReference {

    override fun getElement(): PsiElement = element

    override fun getRangeInElement(): TextRange = TextRange(0, element.getTextLength())

    override fun resolveReference(): @Unmodifiable Collection<DotEnvKeySymbol> {
        return EnvironmentVariablesApi.getKeyDeclarations(element.getProject(), element.getText())
            .map { (it as? DotEnvProperty)?.key ?: it }
            .map { DotEnvKeySymbol(it.text, it.containingFile, it.textRange) }
            .toList()
    }

}