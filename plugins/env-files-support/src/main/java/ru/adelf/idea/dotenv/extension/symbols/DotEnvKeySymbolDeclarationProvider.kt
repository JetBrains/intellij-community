package ru.adelf.idea.dotenv.extension.symbols

import com.intellij.model.psi.PsiSymbolDeclaration
import com.intellij.model.psi.PsiSymbolDeclarationProvider
import com.intellij.psi.PsiElement
import ru.adelf.idea.dotenv.psi.DotEnvKey

class DotEnvKeySymbolDeclarationProvider : PsiSymbolDeclarationProvider {

    override fun getDeclarations(element: PsiElement, offsetInElement: Int): Collection<PsiSymbolDeclaration> {
        return (element as? DotEnvKey)?.let { listOf(DotEnvKeySymbolDeclaration(it)) } ?: emptyList()
    }

}