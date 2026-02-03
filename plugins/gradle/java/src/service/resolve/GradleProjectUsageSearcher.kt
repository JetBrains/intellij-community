// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.gradle.service.resolve

import com.intellij.find.usages.api.PsiUsage
import com.intellij.find.usages.api.Usage
import com.intellij.find.usages.api.UsageSearchParameters
import com.intellij.find.usages.api.UsageSearcher
import com.intellij.util.Query

class GradleProjectUsageSearcher : UsageSearcher {

  override fun collectSearchRequest(parameters: UsageSearchParameters): Query<out Usage>? {
    val projectSymbol = parameters.target as? GradleProjectSymbol ?: return null
    return searchGradleProjectReferences(parameters.project, projectSymbol, parameters.searchScope)
      .mapping(PsiUsage::textUsage)
  }
}
