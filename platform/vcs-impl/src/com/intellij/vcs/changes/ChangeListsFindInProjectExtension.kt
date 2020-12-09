// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.vcs.changes

import com.intellij.find.FindModel
import com.intellij.find.impl.FindInProjectExtension
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.LangDataKeys
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.VcsDataKeys
import com.intellij.openapi.vcs.changes.ChangeListManager
import com.intellij.psi.search.SearchScopeProvider
import com.intellij.psi.search.scope.packageSet.NamedScope
import com.intellij.util.ArrayUtil

class ChangeListsFindInProjectExtension : FindInProjectExtension {

  override fun initModelFromContext(model: FindModel, dataContext: DataContext): Boolean {
    val project = CommonDataKeys.PROJECT.getData(dataContext)
    val module = LangDataKeys.MODULE_CONTEXT.getData(dataContext)
    if (module != null || project == null) return false

    val changeListManager = ChangeListManager.getInstance(project)
    if (!changeListManager.areChangeListsEnabled()) return false

    var changeList = ArrayUtil.getFirstElement(dataContext.getData(VcsDataKeys.CHANGE_LISTS))
    if (changeList == null) {
      val change = ArrayUtil.getFirstElement(dataContext.getData(VcsDataKeys.CHANGES))
      changeList = if (change == null) null else {
        changeListManager.getChangeList(change)
      }
    }

    if (changeList != null) {
      val changeListName = changeList.name
      val changeListsScopeProvider = SearchScopeProvider.EP_NAME.findExtension(ChangeListsSearchScopeProvider::class.java)
      if (changeListsScopeProvider != null) {
        val changeListScope = changeListsScopeProvider
          .getSearchScopes(project, dataContext)
          .firstOrNull { scope -> scope.displayName == changeListName }
        if (changeListScope != null) {
          model.isCustomScope = true
          model.customScopeName = changeListScope.displayName
          model.customScope = changeListScope
          return true
        }
      }
    }

    return false
  }

  override fun getFilteredNamedScopes(project: Project): List<NamedScope> {
    return ChangeListsScopesProvider.getInstance(project).filteredScopes
  }
}