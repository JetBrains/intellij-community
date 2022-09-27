// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.quickDoc

import com.intellij.codeInsight.documentation.DocumentationManager
import com.intellij.codeInsight.documentation.DocumentationManagerProtocol
import com.intellij.lang.documentation.DocumentationLinkHandler
import com.intellij.lang.documentation.DocumentationTarget
import com.intellij.lang.documentation.LinkResolveResult
import com.intellij.lang.documentation.psi.psiDocumentationTarget
import com.intellij.openapi.util.component1
import com.intellij.openapi.util.component2

class KotlinDocumentationLinkHandler : DocumentationLinkHandler {
    override fun resolveLink(target: DocumentationTarget, url: String): LinkResolveResult? {
        if (target !is KotlinDocumentationTarget) {
            return null
        }
        val element = target.element
        if (url.startsWith(DocumentationManagerProtocol.PSI_ELEMENT_PROTOCOL)) {
            val project = element.project
            val (resolved, anchor) = DocumentationManager.targetAndRef(project, url, element)
                ?: return null
            return LinkResolveResult.resolvedTarget(psiDocumentationTarget(resolved,  resolved))
        }
        return null
    }
}