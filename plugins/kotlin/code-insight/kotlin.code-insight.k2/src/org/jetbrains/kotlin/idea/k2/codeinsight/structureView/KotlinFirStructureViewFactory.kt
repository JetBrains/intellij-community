// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.structureView

import com.intellij.ide.structureView.StructureViewBuilder
import com.intellij.ide.structureView.StructureViewModel
import com.intellij.ide.structureView.TreeBasedStructureViewBuilder
import com.intellij.ide.util.treeView.smartTree.NodeProvider
import com.intellij.lang.PsiStructureViewFactory
import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiFile
import org.jetbrains.kotlin.idea.base.util.KotlinSingleClassFileAnalyzer
import org.jetbrains.kotlin.idea.structureView.KotlinStructureViewModel
import org.jetbrains.kotlin.psi.KtFile

private val NODE_PROVIDERS: List<NodeProvider<*>> = listOf(KotlinFirInheritedMembersNodeProvider())

class KotlinFirStructureViewFactory : PsiStructureViewFactory {
    override fun getStructureViewBuilder(psiFile: PsiFile): StructureViewBuilder? {
        val file = psiFile as? KtFile ?: return null
        val extension = KotlinStructureViewExtension.EP_NAME.findFirstSafe { it.isApplicable(file) }
        return extension?.getStructureViewBuilder(psiFile) ?: createDefaultStructureViewBuilder(file)
    }

    companion object {
        fun createDefaultStructureViewBuilder(file: KtFile): TreeBasedStructureViewBuilder {
            val defaultRootNodeShown = !KotlinSingleClassFileAnalyzer.isSingleClassFile(file)
            return object : TreeBasedStructureViewBuilder() {
                override fun createStructureViewModel(editor: Editor?): StructureViewModel =
                    object : KotlinStructureViewModel(file, editor, KotlinFirStructureViewElement(file, file, false)) {
                        override fun getNodeProviders(): Collection<NodeProvider<*>> = NODE_PROVIDERS
                    }

                override fun isRootNodeShown(): Boolean = defaultRootNodeShown
            }
        }
    }
}
