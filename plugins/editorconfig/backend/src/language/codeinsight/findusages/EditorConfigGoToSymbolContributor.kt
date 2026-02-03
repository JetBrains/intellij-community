// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.editorconfig.language.codeinsight.findusages

import com.intellij.editorconfig.common.syntax.psi.EditorConfigDescribableElement
import com.intellij.navigation.ChooseByNameContributorEx
import com.intellij.navigation.NavigationItem
import com.intellij.psi.PsiManager
import com.intellij.psi.SyntaxTraverser
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.util.Processor
import com.intellij.util.indexing.FileBasedIndex
import com.intellij.util.indexing.FindSymbolParameters
import com.intellij.util.indexing.IdFilter
import org.editorconfig.language.index.EDITOR_CONFIG_IDENTIFIER_INDEX_ID
import org.editorconfig.language.schema.descriptors.getDescriptor
import org.editorconfig.language.schema.descriptors.impl.EditorConfigReferenceDescriptor

internal class EditorConfigGoToSymbolContributor : ChooseByNameContributorEx {
  override fun processNames(processor: Processor<in String>, scope: GlobalSearchScope, filter: IdFilter?) {
    FileBasedIndex.getInstance().processAllKeys(EDITOR_CONFIG_IDENTIFIER_INDEX_ID, processor, scope, filter)
  }

  override fun processElementsWithName(name: String, processor: Processor<in NavigationItem>, parameters: FindSymbolParameters) {
    val psiManager = PsiManager.getInstance(parameters.project)
    FileBasedIndex.getInstance().getFilesWithKey(
      EDITOR_CONFIG_IDENTIFIER_INDEX_ID, setOf(name), { file ->
      SyntaxTraverser.psiTraverser(psiManager.findFile(file))
        .filter(EditorConfigDescribableElement::class.java)
        .filter { it.getDescriptor(false) !is EditorConfigReferenceDescriptor }
        .processEach(processor)
    }, parameters.searchScope)
  }
}
