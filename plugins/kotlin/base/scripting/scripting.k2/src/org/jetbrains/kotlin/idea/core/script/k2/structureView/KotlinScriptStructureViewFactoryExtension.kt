// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.core.script.k2.structureView

import com.intellij.ide.structureView.StructureViewBuilder
import com.intellij.ide.structureView.StructureViewModel
import com.intellij.ide.structureView.StructureViewTreeElement
import com.intellij.ide.structureView.TreeBasedStructureViewBuilder
import com.intellij.ide.util.treeView.smartTree.Filter
import com.intellij.ide.util.treeView.smartTree.Grouper
import com.intellij.ide.util.treeView.smartTree.ProvidingTreeModel
import com.intellij.ide.util.treeView.smartTree.Sorter
import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiFile
import com.intellij.ui.tree.LeafState
import org.jetbrains.kotlin.idea.k2.codeinsight.structureView.KotlinFirStructureViewFactory
import org.jetbrains.kotlin.idea.k2.codeinsight.structureView.KotlinStructureViewExtension
import org.jetbrains.kotlin.psi.KtFile

class KotlinScriptStructureViewFactoryExtension : KotlinStructureViewExtension {
    override fun isApplicable(file: KtFile): Boolean = file.isScript()

    override fun getStructureViewBuilder(psiFile: PsiFile): StructureViewBuilder {
        val file = psiFile as KtFile
        val defaultBuilder = KotlinFirStructureViewFactory.createDefaultStructureViewBuilder(file)
        return object : TreeBasedStructureViewBuilder() {
            override fun createStructureViewModel(editor: Editor?): StructureViewModel {
                val defaultModel = defaultBuilder.createStructureViewModel(editor)
                val elementInfoProvider = defaultModel as StructureViewModel.ElementInfoProvider
                val providingTreeModel = defaultModel as ProvidingTreeModel

                return object : StructureViewModel by defaultModel,
                    StructureViewModel.ElementInfoProvider,
                    ProvidingTreeModel by providingTreeModel {
                    override fun getRoot(): StructureViewTreeElement =
                        createScriptFileRoot(file, defaultModel.root)

                    override fun getGroupers(): Array<Grouper> = defaultModel.groupers

                    override fun getSorters(): Array<Sorter> = defaultModel.sorters

                    override fun getFilters(): Array<Filter> = defaultModel.filters

                    override fun isAlwaysShowsPlus(element: StructureViewTreeElement): Boolean =
                        elementInfoProvider.isAlwaysShowsPlus(element)

                    override fun isAlwaysLeaf(element: StructureViewTreeElement): Boolean =
                        (element as? LeafState.Supplier)?.leafState == LeafState.ALWAYS ||
                                elementInfoProvider.isAlwaysLeaf(element)
                }
            }

            override fun isRootNodeShown(): Boolean = true
        }
    }
}
