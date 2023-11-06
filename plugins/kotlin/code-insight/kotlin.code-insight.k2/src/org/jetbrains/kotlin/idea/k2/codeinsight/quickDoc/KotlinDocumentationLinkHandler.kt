// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.quickDoc

import com.intellij.codeInsight.documentation.DocumentationManagerProtocol
import com.intellij.lang.documentation.psi.psiDocumentationTarget
import com.intellij.platform.backend.documentation.DocumentationLinkHandler
import com.intellij.platform.backend.documentation.DocumentationTarget
import com.intellij.platform.backend.documentation.LinkResolveResult
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.kdoc.psi.impl.KDocName
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.psi.KtElement
import org.jetbrains.kotlin.psi.KtPsiFactory

class KotlinDocumentationLinkHandler : DocumentationLinkHandler {
    override fun resolveLink(target: DocumentationTarget, url: String): LinkResolveResult? {
        if (target !is KotlinDocumentationTarget) {
            return null
        }
        val element = target.element
        if (url.startsWith(DocumentationManagerProtocol.PSI_ELEMENT_PROTOCOL) && element is KtElement) {
            val names = url.substring(DocumentationManagerProtocol.PSI_ELEMENT_PROTOCOL.length).split('.')
            val target = resolveKDocLink(names, element) ?: return null
            return LinkResolveResult.resolvedTarget(psiDocumentationTarget(target, target))
        }
        return null
    }
}

fun resolveKDocLink(names: List<String>, element: KtElement): PsiElement? {
    val ktPsiFactory = KtPsiFactory(element.project)

    val fragment = ktPsiFactory.createBlockCodeFragment("/**[${names.joinToString(".")}]*/ val __p = 42", element)
    val docRef = PsiTreeUtil.findChildOfType(fragment, KDocName::class.java)
    docRef?.reference?.resolve()?.let { return it }

    if (names.size == 1) {
        val locals = mutableListOf<PsiElement>()
        analyze(element) {
            val shortName = Name.identifier(names.first())
            val scope = getPackageSymbolIfPackageExists(element.containingKtFile.packageFqName)?.getPackageScope()
            if (scope != null) {
                scope.getCallableSymbols(shortName).mapNotNullTo(locals) { it.psi }
                scope.getClassifierSymbols(shortName).mapNotNullTo(locals) { it.psi }
            }
        }
        if (locals.isNotEmpty()) {
            return locals.first()
        }
    }
    return null
}