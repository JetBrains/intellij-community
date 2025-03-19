// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.structureView

import com.intellij.ide.structureView.StructureViewTreeElement
import com.intellij.ide.structureView.logical.LogicalStructureTreeElementProvider
import com.intellij.psi.PsiClass
import org.jetbrains.kotlin.idea.KotlinLanguage
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.uast.toUElement

class K2ClassLogicalStructureTreeElementProvider: LogicalStructureTreeElementProvider<PsiClass> {

    override fun getModelClass(): Class<PsiClass> = PsiClass::class.java

    override fun getTreeElement(model: PsiClass): StructureViewTreeElement? {
        if (model.language != KotlinLanguage.INSTANCE) return null
        val ktClass = model.toUElement()?.sourcePsi as? KtClass ?: return null
        return KotlinFirStructureViewElement(ktClass, ktClass, false)
    }

}