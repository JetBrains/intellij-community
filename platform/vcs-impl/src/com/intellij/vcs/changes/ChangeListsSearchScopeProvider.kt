// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.vcs.changes

import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.VcsBundle
import com.intellij.psi.search.DefaultSearchScopeProviders
import com.intellij.psi.search.SearchScope
import com.intellij.psi.search.SearchScopeProvider
import java.util.*

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
    return result
  }
}