// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.codeinsights.impl.base.intentions

import com.intellij.ide.util.PsiClassListCellRenderer
import com.intellij.ide.util.PsiClassRenderingInfo
import com.intellij.ide.util.PsiElementListCellRenderer
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.psi.KtEnumEntry
import org.jetbrains.kotlin.psi.psiUtil.containingClassOrObject

class ClassListCellRenderer : PsiElementListCellRenderer<PsiElement>() {
    private val psiClassRenderer = PsiClassListCellRenderer()

    override fun getComparator(): Comparator<PsiElement> {
        val baseComparator = psiClassRenderer.comparator
        return Comparator { o1, o2 ->
            when {
                o1 is KtEnumEntry && o2 is KtEnumEntry -> o1.name!!.compareTo(o2.name!!)
                o1 is KtEnumEntry -> -1
                o2 is KtEnumEntry -> 1
                o1 is PsiClass && o2 is PsiClass -> baseComparator.compare(o1, o2)
                else -> 0
            }
        }
    }

    override fun getElementText(element: PsiElement?): String? {
        return when (element) {
            is KtEnumEntry -> element.name
            is PsiClass -> psiClassRenderer.getElementText(element)
            else -> null
        }
    }

    override fun getContainerText(element: PsiElement?, name: String?): String? {
        return when (element) {
            is KtEnumEntry -> element.containingClassOrObject?.fqName?.asString()
            is PsiClass -> PsiClassRenderingInfo.getContainerTextStatic(element)
            else -> null
        }
    }
}
