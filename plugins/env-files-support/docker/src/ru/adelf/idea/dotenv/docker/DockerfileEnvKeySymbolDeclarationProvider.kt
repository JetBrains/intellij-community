package ru.adelf.idea.dotenv.docker

import com.intellij.docker.dockerFile.parser.psi.DockerFileEnvRegularDeclaration
import com.intellij.model.psi.PsiSymbolDeclaration
import com.intellij.model.psi.PsiSymbolDeclarationProvider
import com.intellij.psi.PsiElement
import ru.adelf.idea.dotenv.extension.symbols.DotEnvKeySymbolDeclaration

class DockerfileEnvKeySymbolDeclarationProvider: PsiSymbolDeclarationProvider {

    override fun getDeclarations(element: PsiElement, offsetInElement: Int): Collection<PsiSymbolDeclaration> {
        return (element as? DockerFileEnvRegularDeclaration)?.let {
            listOf(DotEnvKeySymbolDeclaration(it.declaredName))
        } ?: emptyList()
    }

}