// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.vcs.changes

import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.displayUrlRelativeToProject
import com.intellij.openapi.vcs.VcsBundle
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.search.DefaultSearchScopeProviders
import com.intellij.psi.search.SearchScope
import com.intellij.psi.search.SearchScopeProvider
import com.intellij.psi.util.PsiUtilCore

class ChangeListsSearchScopeProvider : SearchScopeProvider {
  override fun getDisplayName(): String? {
    return VcsBundle.message("change.list.scope.provider.local.changes")
  }

  override fun getSearchScopes(project: Project,
                               dataContext: DataContext): List<SearchScope> {
    val result: MutableList<SearchScope> = ArrayList()
    val changeLists = ChangeListsScopesProvider.getInstance(project).filteredScopes
    if (!changeLists.isEmpty()) {
      for (changeListScope in changeLists) {
        result.add(DefaultSearchScopeProviders.wrapNamedScope(project, changeListScope!!, false))
      }
    }

    result.add(VcsChangesLocalSearchScope(project, VcsBundle.message("change.list.scope.provider.only.changes"), null, false))

    val psiFile = CommonDataKeys.PSI_FILE.getData(dataContext)
                  ?: (
                    if (ApplicationManager.getApplication().isDispatchThread)
                      FileEditorManager.getInstance(project).selectedTextEditor
                    else null)
                    ?.let { PsiDocumentManager.getInstance(project).getPsiFile(it.document) }

    if (psiFile != null) {
      val virtualFile = PsiUtilCore.getVirtualFile(psiFile)
      if (virtualFile != null) {
        val localUrl = displayUrlRelativeToProject(virtualFile, virtualFile.presentableUrl, project, isIncludeFilePath = true,
                                                     moduleOnTheLeft = false)
        result.add(
          VcsChangesLocalSearchScope(project, VcsBundle.message("change.list.scope.provider.only.changes.in.file", localUrl), arrayOf(virtualFile), false))
      }
    }
    return result
  }
}