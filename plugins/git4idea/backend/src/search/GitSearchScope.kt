// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.search

import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.FileIndexFacade
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.ProjectScopeImpl

internal abstract class GitSearchScope(project: Project) : ProjectScopeImpl(project, FileIndexFacade.getInstance(project)) {
  override fun intersectWith(scope: GlobalSearchScope): GlobalSearchScope = defaultIntersectWith(scope)
}