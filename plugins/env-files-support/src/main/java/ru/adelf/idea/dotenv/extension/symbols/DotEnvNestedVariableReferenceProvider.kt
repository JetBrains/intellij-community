package ru.adelf.idea.dotenv.extension.symbols

import com.intellij.model.Symbol
import com.intellij.model.psi.PsiExternalReferenceHost
import com.intellij.model.psi.PsiSymbolReference
import com.intellij.model.psi.PsiSymbolReferenceHints
import com.intellij.model.psi.PsiSymbolReferenceProvider
import com.intellij.model.search.SearchRequest
import com.intellij.openapi.project.Project
import ru.adelf.idea.dotenv.psi.DotEnvNestedVariableKey

class DotEnvNestedVariableReferenceProvider: PsiSymbolReferenceProvider {

    override fun getReferences(element: PsiExternalReferenceHost, hints: PsiSymbolReferenceHints): Collection<PsiSymbolReference> {
        return (element as? DotEnvNestedVariableKey)?.let { listOf(DotEnvKeyReference(it)) }
               ?: emptyList()
    }

    override fun getSearchRequests(project: Project, target: Symbol): Collection<SearchRequest> {
        return emptyList()
    }

}