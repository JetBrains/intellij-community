// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.quickDoc

import com.intellij.codeInsight.documentation.DocumentationManagerProtocol
import com.intellij.lang.documentation.psi.psiDocumentationTargets
import com.intellij.platform.backend.documentation.DocumentationLinkHandler
import com.intellij.platform.backend.documentation.DocumentationTarget
import com.intellij.platform.backend.documentation.LinkResolveResult
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil.findChildOfType
import org.jetbrains.kotlin.kdoc.psi.impl.KDocName
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
            return LinkResolveResult.resolvedTarget(psiDocumentationTargets(target, target).first()) //TODO support multi-targeting
        }
        return null
    }
}

fun resolveKDocLink(names: List<String>, element: KtElement): PsiElement? {
    val ktPsiFactory = KtPsiFactory(element.project)
    val fragment = ktPsiFactory.createBlockCodeFragment("/**[${names.joinToString(".")}]*/ val __p = 42", element)
    return findChildOfType<KDocName>(fragment, KDocName::class.java)?.reference?.resolve()
}