// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.hierarchy.types

import com.intellij.codeInsight.navigation.GotoTargetHandler
import com.intellij.ide.hierarchy.HierarchyNodeDescriptor
import com.intellij.ide.hierarchy.type.TypeHierarchyNodeDescriptor
import com.intellij.openapi.editor.markup.TextAttributes
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ui.util.CompositeAppearance
import com.intellij.openapi.util.Comparing
import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.psi.KtClassOrObject
import java.awt.Font

class KotlinTypeHierarchyNodeDescriptor(
    project: Project,
    parentDescriptor: HierarchyNodeDescriptor?,
    classOrFunctionalExpression: PsiElement,
    isBase: Boolean
) : HierarchyNodeDescriptor(project, parentDescriptor, classOrFunctionalExpression, isBase) {

    override fun update(): Boolean {
        var changes = super.update()

        if (psiElement == null) {
            return invalidElement()
        }

        if (changes && myIsBase) {
            setIcon(getBaseMarkerIcon(icon))
        }

        val psiElement = psiElement

        val oldText = myHighlightedText

        myHighlightedText = CompositeAppearance()

        var classNameAttributes: TextAttributes? = null
        if (myColor != null) {
            classNameAttributes = TextAttributes(myColor, null, null, null, Font.PLAIN)
        }

        if (psiElement is KtClassOrObject) {
            val targetPresentation = GotoTargetHandler.computePresentation(psiElement, false)
            myHighlightedText.ending.addText(targetPresentation.presentableText, classNameAttributes)
            myHighlightedText.ending.addText(" (" + (targetPresentation.containerText ?: "") + ")", getPackageNameAttributes())
        }

        myName = myHighlightedText.text

        if (!Comparing.equal(myHighlightedText, oldText)) {
            changes = true
        }
        return changes
    }

    companion object {
        fun createTypeHierarchyDescriptor(
            klass: PsiElement,
            descriptor: HierarchyNodeDescriptor?
        ): HierarchyNodeDescriptor = when (klass) {
            is KtClassOrObject -> KotlinTypeHierarchyNodeDescriptor(klass.project, descriptor, klass, false)
            else -> TypeHierarchyNodeDescriptor(klass.project, descriptor, klass, false)
        }
    }
}
