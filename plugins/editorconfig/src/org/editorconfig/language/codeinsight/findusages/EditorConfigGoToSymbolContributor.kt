// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.editorconfig.language.codeinsight.findusages

import com.intellij.navigation.ChooseByNameContributor
import com.intellij.navigation.NavigationItem
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.NavigatablePsiElement
import com.intellij.psi.PsiManager
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.util.CommonProcessors
import com.intellij.util.indexing.FileBasedIndex
import org.editorconfig.language.index.EditorConfigIdentifierIndex
import org.editorconfig.language.psi.impl.EditorConfigIdentifierFinderVisitor
import org.editorconfig.language.psi.interfaces.EditorConfigDescribableElement

class EditorConfigGoToSymbolContributor : ChooseByNameContributor {
  override fun getNames(project: Project, includeNonProjectItems: Boolean) =
    FileBasedIndex.getInstance().getAllKeys(EditorConfigIdentifierIndex.id, project).toTypedArray()

  override fun getItemsByName(name: String, pattern: String, project: Project, includeNonProjectItems: Boolean): Array<NavigationItem> {
    val files = CommonProcessors.CollectProcessor<VirtualFile>()
    val scope = GlobalSearchScope.allScope(project)
    FileBasedIndex.getInstance().getFilesWithKey(EditorConfigIdentifierIndex.id, setOf(name), files, scope)

    val result = mutableListOf<NavigatablePsiElement>()
    val finder = object : EditorConfigIdentifierFinderVisitor() {
      override fun collectIdentifier(identifier: EditorConfigDescribableElement) {
        result.add(identifier)
      }
    }

    files.results.mapNotNull(PsiManager.getInstance(project)::findFile).forEach { it.accept(finder) }
    return result.toTypedArray()
  }
}
