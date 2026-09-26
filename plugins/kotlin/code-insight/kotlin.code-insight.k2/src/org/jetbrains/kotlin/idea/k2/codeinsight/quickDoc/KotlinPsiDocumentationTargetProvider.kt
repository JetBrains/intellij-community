// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.quickDoc

import com.intellij.codeInsight.javadoc.JavaDocExternalFilter
import com.intellij.lang.documentation.psi.psiDocumentationTargets
import com.intellij.openapi.util.TextRange
import com.intellij.platform.backend.documentation.DocumentationTarget
import com.intellij.platform.backend.documentation.DocumentationTargetProvider
import com.intellij.platform.backend.documentation.InlineDocumentation
import com.intellij.platform.backend.documentation.InlineDocumentationProvider
import com.intellij.platform.backend.documentation.PsiDocumentationTargetProvider
import com.intellij.psi.PsiDocCommentBase
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiJavaFile
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.util.parentOfType
import org.jetbrains.kotlin.idea.KotlinLanguage
import org.jetbrains.kotlin.idea.highlighting.kdoc.KDocRenderer.renderKDoc
import org.jetbrains.kotlin.idea.references.KDocReference
import org.jetbrains.kotlin.idea.references.mainReference
import org.jetbrains.kotlin.kdoc.psi.api.KDoc
import org.jetbrains.kotlin.kdoc.psi.impl.KDocName
import org.jetbrains.kotlin.psi.KtDeclaration
import org.jetbrains.kotlin.psi.KtFile

class KotlinPsiDocumentationTargetProvider : PsiDocumentationTargetProvider {
    override fun documentationTarget(element: PsiElement, originalElement: PsiElement?): DocumentationTarget? {
        return if (element.language.`is`(KotlinLanguage.INSTANCE)) {
            KotlinDocumentationTarget(element, originalElement).takeUnless {
                val navigationElement = element.navigationElement

                // there are cases when documentation viewed from Java files
                // should NOT be based on Kotlin representation, but on original Java
                if (originalElement?.containingFile !is PsiJavaFile) {
                    return@takeUnless false
                }

                // top level functions and properties are accessible via file-wrapper class
                // `foo.kt` is represented in Java as `FooKt`.
                navigationElement is KtFile
            }
        } else {
            null
        }
    }
}

class KotlinDocumentationTargetProvider : DocumentationTargetProvider {
    override fun documentationTargets(file: PsiFile, offset: Int): List<DocumentationTarget> {
        val element = file.findElementAt(offset) ?: return emptyList()
        return if (element.isModifier()) {
            arrayListOf(KotlinDocumentationTarget(element, element))
        } else {
            handleKDocLinkIfNeeded(element)
        }
    }

    /**
     * A KDoc link may be resolved to multiple declarations, e.g., to several overloads of the same function.
     * In such cases [KDocReference.resolve] intentionally uses `multiResolve(incompleteCode).singleOrNull()` so that navigation can offer
     * all the candidates in a drop-down menu.
     * Even though quick doc popups support multiple resolved symbols, this breaks the documentation target search which always calls a regular `resolve`
     * (in [com.intellij.codeInsight.TargetElementUtilBase.getReferencedElement] used by default [DocumentationTargetProvider]s).
     *
     * [handleKDocLinkIfNeeded] returns a list of targets resolved from [element] if it's a KDoc link, an empty list otherwise.
     * As resolved elements may come from different languages, [psiDocumentationTargets] is used to properly wrap every resolved PSI
     * into a documentation target wrapper.
     */
    private fun handleKDocLinkIfNeeded(element: PsiElement): List<DocumentationTarget> {
        val kDocName = element.parentOfType<KDocName>(withSelf = true) ?: return emptyList()
        val resolvedResults = kDocName.mainReference.multiResolve(incompleteCode = false)

        return resolvedResults.mapNotNull { resolvedElement ->
            val targetPsi = resolvedElement.element ?: return@mapNotNull null
            psiDocumentationTargets(targetPsi, element)
        }.flatten()
    }
}

private class KotlinInlineDocumentation(private val comment: PsiDocCommentBase, private val declaration: KtDeclaration): InlineDocumentation {
    override fun getDocumentationRange(): TextRange {
        return comment.textRange
    }

    override fun getDocumentationOwnerRange(): TextRange? {
        return declaration.textRange
    }

    override fun renderText(): String? {
        val docComment = comment as? KDoc ?: return null
        val result = StringBuilder().also {
            it.renderKDoc(docComment.getDefaultSection(), docComment.getAllSections())
        }

        @Suppress("HardCodedStringLiteral")
        return JavaDocExternalFilter.filterInternalDocInfo(result.toString())
    }

    override fun getOwnerTarget(): DocumentationTarget {
        return KotlinDocumentationTarget(declaration, declaration)
    }
}

class KotlinInlineDocumentationProvider: InlineDocumentationProvider {
    override fun inlineDocumentationItems(file: PsiFile?): Collection<InlineDocumentation> {
        if (file !is KtFile) return emptyList()

        val result = mutableListOf<InlineDocumentation>()
        PsiTreeUtil.processElements(file) {
            val declaration = it as? KtDeclaration
            val comment = (declaration)?.docComment
            if (comment != null) {
                result.add(KotlinInlineDocumentation(comment, declaration))
            }
            true
        }
        return result
    }

    override fun findInlineDocumentation(
        file: PsiFile,
        textRange: TextRange
    ): InlineDocumentation? {
        val comment = PsiTreeUtil.getParentOfType(file.findElementAt(textRange.startOffset), PsiDocCommentBase::class.java) ?: return null
        if (comment.textRange == textRange) {
            val declaration = comment.owner as? KtDeclaration ?: return null
            return KotlinInlineDocumentation(comment, declaration)
        }
        return null
    }
}