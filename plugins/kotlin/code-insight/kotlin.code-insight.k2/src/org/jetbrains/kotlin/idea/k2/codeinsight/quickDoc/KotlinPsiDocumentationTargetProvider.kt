// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.quickDoc

import com.intellij.codeInsight.javadoc.JavaDocExternalFilter
import com.intellij.openapi.util.TextRange
import com.intellij.platform.backend.documentation.*
import com.intellij.psi.PsiDocCommentBase
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiJavaFile
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.kotlin.asJava.elements.KtLightDeclaration
import org.jetbrains.kotlin.idea.KotlinLanguage
import org.jetbrains.kotlin.idea.kdoc.KDocRenderer.renderKDoc
import org.jetbrains.kotlin.kdoc.psi.api.KDoc
import org.jetbrains.kotlin.psi.KtDeclaration
import org.jetbrains.kotlin.psi.KtFile

class KotlinPsiDocumentationTargetProvider : PsiDocumentationTargetProvider {
    override fun documentationTarget(element: PsiElement, originalElement: PsiElement?): DocumentationTarget? {
        return if (element.language.`is`(KotlinLanguage.INSTANCE)) {
            KotlinDocumentationTarget(element, originalElement).takeUnless {
                // show documentation based on java presentation
                val navigationElement = element.navigationElement
                navigationElement is KtFile && originalElement?.containingFile is PsiJavaFile || navigationElement is KtLightDeclaration<*, *> && navigationElement.kotlinOrigin == null
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
        } else emptyList()
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