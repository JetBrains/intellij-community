// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.structureView

import com.intellij.ide.structureView.StructureViewModel
import com.intellij.ide.structureView.StructureViewModelBase
import com.intellij.ide.structureView.StructureViewTreeElement
import com.intellij.ide.structureView.impl.java.VisibilitySorter
import com.intellij.ide.util.treeView.smartTree.*
import com.intellij.openapi.editor.Editor
import com.intellij.psi.NavigatablePsiElement
import com.intellij.psi.PsiElement
import com.intellij.ui.IconManager
import com.intellij.ui.PlatformIcons
import org.jetbrains.kotlin.idea.codeInsight.KotlinCodeInsightBundle
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.containingClassOrObject
import org.jetbrains.kotlin.psi.psiUtil.isPropertyParameter

open class KotlinStructureViewModel(ktFile: KtFile, editor: Editor?, rootElement : StructureViewTreeElement) :
    StructureViewModelBase(ktFile, editor, rootElement),
    StructureViewModel.ElementInfoProvider {

    init {
        withSorters(KotlinVisibilitySorter, Sorter.ALPHA_SORTER)
    }

    override fun isSuitable(element: PsiElement?): Boolean = isStructureViewNode(element)

    override fun getFilters(): Array<Filter> = FILTERS

    override fun isAlwaysShowsPlus(element: StructureViewTreeElement): Boolean {
        val value = element.value
        return (value is KtClassOrObject && value !is KtEnumEntry) || value is KtFile
    }

    override fun isAlwaysLeaf(element: StructureViewTreeElement): Boolean {
        // Local declarations can in any other declaration
        return false
    }

    companion object {
        private val FILTERS = arrayOf(PropertiesFilter, PublicElementsFilter)
    }
}

object KotlinVisibilitySorter : VisibilitySorter() {
    override fun getComparator(): Comparator<Any> = Comparator { a1, a2 -> a1.accessLevel() - a2.accessLevel() }

    private fun Any.accessLevel() = (this as? AbstractKotlinStructureViewElement)?.accessLevel ?: Int.MAX_VALUE

    override fun getName(): String = ID

    const val ID: String = "KOTLIN_VISIBILITY_SORTER"
}

object PublicElementsFilter : Filter {
    override fun isVisible(treeNode: TreeElement): Boolean {
        return (treeNode as? AbstractKotlinStructureViewElement)?.isPublic ?: true
    }

    override fun getPresentation(): ActionPresentation {
        return ActionPresentationData(
            KotlinCodeInsightBundle.message("show.non.public"),
            null,
            IconManager.getInstance().getPlatformIcon(PlatformIcons.Private)
        )
    }

    override fun getName(): String = ID

    override fun isReverted(): Boolean = true

    const val ID: String = "KOTLIN_SHOW_NON_PUBLIC"
}

object PropertiesFilter : Filter {
    override fun isVisible(treeNode: TreeElement): Boolean {
        val element = (treeNode as? AbstractKotlinStructureViewElement)?.element
        val isProperty = element is KtProperty && element.isMember || element is KtParameter && element.isPropertyParameter()
        return !isProperty
    }

    override fun getPresentation(): ActionPresentation {
        return ActionPresentationData(KotlinCodeInsightBundle.message("show.properties"), null, IconManager.getInstance().getPlatformIcon(PlatformIcons.Property))
    }

    override fun getName(): String = ID

    override fun isReverted(): Boolean = true

    const val ID: String = "KOTLIN_SHOW_PROPERTIES"
}

/**
 * Required until 2 implementations would be merged
 */
interface AbstractKotlinStructureViewElement {
    val accessLevel : Int?
    val isPublic : Boolean
    val element : NavigatablePsiElement
}