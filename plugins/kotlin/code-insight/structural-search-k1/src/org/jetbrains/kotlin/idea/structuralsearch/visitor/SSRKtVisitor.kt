// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.structuralsearch.visitor

import com.intellij.psi.PsiElement
import com.intellij.psi.impl.source.tree.LeafPsiElement
import org.jetbrains.kotlin.kdoc.psi.api.KDoc
import org.jetbrains.kotlin.kdoc.psi.impl.KDocLink
import org.jetbrains.kotlin.kdoc.psi.impl.KDocSection
import org.jetbrains.kotlin.kdoc.psi.impl.KDocTag
import org.jetbrains.kotlin.psi.KtVisitorVoid

/**
 * Implements missing functions from [org.jetbrains.kotlin.psi.KtVisitorVoid].
 */
open class SSRKtVisitor : KtVisitorVoid() {
    override fun visitElement(element: PsiElement) {
        super.visitElement(element)
        when (element) {
            is LeafPsiElement -> visitLeafPsiElement(element)
            is KDoc -> visitKDoc(element)
            is KDocSection -> visitKDocSection(element)
            is KDocTag -> visitKDocTag(element)
            is KDocLink -> visitKDocLink(element)
        }
    }

    open fun visitLeafPsiElement(leafPsiElement: LeafPsiElement) {
    }

    open fun visitKDoc(kDoc: KDoc) {
    }

    open fun visitKDocSection(section: KDocSection) {
    }

    open fun visitKDocTag(tag: KDocTag) {
    }

    open fun visitKDocLink(link: KDocLink) {
    }
}