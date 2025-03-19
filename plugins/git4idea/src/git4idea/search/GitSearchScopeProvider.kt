// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.search

import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.project.Project
import com.intellij.psi.search.SearchScope
import com.intellij.psi.search.SearchScopeProvider

internal class GitSearchScopeProvider : SearchScopeProvider {
  override fun getGeneralSearchScopes(project: Project, dataContext: DataContext): List<SearchScope> =
    listOfNotNull(GitIgnoreSearchScope.getSearchScope(project), GitTrackedSearchScope.getSearchScope(project))
}
