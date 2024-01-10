// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.goto

import com.intellij.ide.actions.SearchEverywhereClassifier
import com.intellij.ide.actions.searcheverywhere.PSIPresentationBgRendererWrapper
import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.asJava.unwrapped
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.KtNamedDeclaration
import org.jetbrains.kotlin.utils.addToStdlib.safeAs
import java.awt.Component
import javax.swing.JList

class KotlinSearchEverywhereClassifier : SearchEverywhereClassifier {
    override fun isClass(o: Any?) = o is KtClassOrObject
    override fun isSymbol(o: Any?) = o is KtNamedDeclaration
    override fun getVirtualFile(o: Any) = o.safeAs<PsiElement>()?.containingFile?.virtualFile
    override fun getListCellRendererComponent(
        list: JList<*>,
        value: Any?,
        index: Int,
        isSelected: Boolean,
        cellHasFocus: Boolean
    ): Component? = PSIPresentationBgRendererWrapper.toPsi(value)?.unwrapped?.safeAs<KtNamedDeclaration>()?.let {
      KotlinSearchEverywherePsiRenderer.getInstance(it.project).getListCellRendererComponent(
        list,
        it,
        index,
        isSelected,
        isSelected
      )
    }
}
