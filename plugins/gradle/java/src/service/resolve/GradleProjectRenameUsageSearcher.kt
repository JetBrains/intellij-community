// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.gradle.service.resolve

import com.intellij.find.usages.api.PsiUsage
import com.intellij.refactoring.rename.api.PsiModifiableRenameUsage
import com.intellij.refactoring.rename.api.RenameUsage
import com.intellij.refactoring.rename.api.RenameUsageSearchParameters
import com.intellij.refactoring.rename.api.RenameUsageSearcher
import com.intellij.util.Query

class GradleProjectRenameUsageSearcher : RenameUsageSearcher {

  override fun collectSearchRequests(parameters: RenameUsageSearchParameters): Collection<Query<out RenameUsage>> {
    val projectSymbol = parameters.target as? GradleProjectSymbol ?: return emptyList()
    val query = searchGradleProjectReferences(parameters.project, projectSymbol, parameters.searchScope)
      .mapping {
        PsiModifiableRenameUsage.defaultPsiModifiableRenameUsage(PsiUsage.textUsage(it))
      }
    return listOf(query)
  }
}
