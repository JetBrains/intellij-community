// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.k2.codeinsight.structureView

import com.intellij.ide.util.InheritedMembersNodeProvider
import com.intellij.ide.util.treeView.smartTree.TreeElement
import com.intellij.psi.NavigatablePsiElement
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.symbols.KtClassOrObjectSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KtSymbolOrigin
import org.jetbrains.kotlin.psi.KtClassOrObject

class KotlinFirInheritedMembersNodeProvider : InheritedMembersNodeProvider<TreeElement>() {
    override fun provideNodes(node: TreeElement): Collection<TreeElement> {
        if (node !is KotlinFirStructureViewElement) return listOf()

        val element = node.element as? KtClassOrObject ?: return listOf()

        analyze(element) {
            
            val children = ArrayList<TreeElement>()
            val descriptor = element.getSymbol() as? KtClassOrObjectSymbol ?: return listOf()
            
            descriptor.getMemberScope().getAllSymbols().forEach { memberDescriptor ->
                if (memberDescriptor.origin == KtSymbolOrigin.INTERSECTION_OVERRIDE) return@forEach
                if (memberDescriptor is KtClassOrObjectSymbol) return@forEach
                val psi = memberDescriptor.psi
                if (psi is NavigatablePsiElement) {
                    children.add(KotlinFirStructureViewElement(psi, element, memberDescriptor.createPointer(), true))
                }
            }

            return children
        }
    }
}