// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.kdoc

import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.kotlin.kdoc.parser.KDocKnownTag
import org.jetbrains.kotlin.kdoc.psi.api.KDoc
import org.jetbrains.kotlin.kdoc.psi.impl.KDocSection
import org.jetbrains.kotlin.kdoc.psi.impl.KDocTag
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.findDescendantOfType
import org.jetbrains.kotlin.psi.psiUtil.getChildOfType
import org.jetbrains.kotlin.psi.psiUtil.getChildrenOfType
import org.jetbrains.kotlin.psi.psiUtil.isPropertyParameter
import org.jetbrains.kotlin.util.capitalizeDecapitalize.toLowerCaseAsciiOnly

fun KtElement.findKDocByPsi(): KDocContent? {
    return this.lookupOwnedKDoc()
        ?: this.lookupKDocInContainer()
}

private fun KtElement.lookupOwnedKDoc(): KDocContent? {
    // KDoc for primary constructor is located inside of its class KDoc
    val psiDeclaration = when (this) {
        is KtPrimaryConstructor -> getContainingClassOrObject()
        else -> this
    }

    if (psiDeclaration is KtDeclaration) {
        val kdoc = psiDeclaration.docComment
        if (kdoc != null) {
            if (this is KtConstructor<*>) {
                // ConstructorDescriptor resolves to the same JetDeclaration
                val constructorSection = kdoc.findSectionByTag(KDocKnownTag.CONSTRUCTOR)
                if (constructorSection != null) {
                    // if annotated with @constructor tag and the caret is on constructor definition,
                    // then show @constructor description as the main content, and additional sections
                    // that contain @param tags (if any), as the most relatable ones
                    // practical example: val foo = Fo<caret>o("argument") -- show @constructor and @param content
                    val paramSections = kdoc.findSectionsContainingTag(KDocKnownTag.PARAM)
                    return KDocContent(constructorSection, paramSections)
                }
            }
            return KDocContent(kdoc.getDefaultSection(), kdoc.getAllSections())
        }
    }
    return null
}

/**
 * Looks for sections that have a deeply nested [tag],
 * as opposed to [KDoc.findSectionByTag], which only looks among the top level
 */
private fun KDoc.findSectionsContainingTag(tag: KDocKnownTag): List<KDocSection> {
    return getChildrenOfType<KDocSection>()
        .filter { it.findTagByName(tag.name.toLowerCaseAsciiOnly()) != null }
}

private fun KtElement.lookupKDocInContainer(): KDocContent? {
    val subjectName = name
    val containingDeclaration =
        PsiTreeUtil.findFirstParent(this, true) {
            it is KtDeclarationWithBody && it !is KtPrimaryConstructor
                    || it is KtClassOrObject
        }

    val containerKDoc = containingDeclaration?.getChildOfType<KDoc>()
    if (containerKDoc == null || subjectName == null) return null
    val propertySection = containerKDoc.findSectionByTag(KDocKnownTag.PROPERTY, subjectName)
    val paramTag = containerKDoc.findDescendantOfType<KDocTag> { it.knownTag == KDocKnownTag.PARAM && it.getSubjectName() == subjectName }

    val primaryContent = when {
        // class Foo(val <caret>s: String)
        this is KtParameter && this.isPropertyParameter() -> propertySection ?: paramTag
        // fun some(<caret>f: String) || class Some<<caret>T: Base> || Foo(<caret>s = "argument")
        this is KtParameter || this is KtTypeParameter -> paramTag
        // if this property is declared separately (outside primary constructor), but it's for some reason
        // annotated as @property in class's description, instead of having its own KDoc
        this is KtProperty && containingDeclaration is KtClassOrObject -> propertySection
        else -> null
    }
    return primaryContent?.let {
        // makes little sense to include any other sections, since we found
        // documentation for a very specific element, like a property/param
        KDocContent(it, sections = emptyList())
    }
}
