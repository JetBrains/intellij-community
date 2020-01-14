// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.editorconfig.language.codeinsight.findusages

import com.intellij.navigation.ChooseByNameContributorEx
import com.intellij.navigation.NavigationItem
import com.intellij.psi.PsiManager
import com.intellij.psi.SyntaxTraverser
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.util.Processor
import com.intellij.util.indexing.FileBasedIndex
import com.intellij.util.indexing.FindSymbolParameters
import com.intellij.util.indexing.IdFilter
import org.editorconfig.language.index.EditorConfigIdentifierIndex
import org.editorconfig.language.psi.interfaces.EditorConfigDescribableElement

class EditorConfigGoToSymbolContributor : ChooseByNameContributorEx {
  override fun processNames(processor: Processor<in String>, scope: GlobalSearchScope, filter: IdFilter?) {
    FileBasedIndex.getInstance().processAllKeys(EditorConfigIdentifierIndex.id, processor, scope, filter)
  }

  override fun processElementsWithName(name: String, processor: Processor<in NavigationItem>, parameters: FindSymbolParameters) {
    val psiManager = PsiManager.getInstance(parameters.project)
    FileBasedIndex.getInstance().getFilesWithKey(
      EditorConfigIdentifierIndex.id, setOf(name), { file ->
      SyntaxTraverser.psiTraverser(psiManager.findFile(file))
        .filter(EditorConfigDescribableElement::class.java)
        .processEach(processor)
    }, parameters.searchScope)
  }
}
