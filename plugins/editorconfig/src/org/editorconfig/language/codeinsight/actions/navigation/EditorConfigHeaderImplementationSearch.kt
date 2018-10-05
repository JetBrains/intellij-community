// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.editorconfig.language.codeinsight.actions.navigation

import com.intellij.openapi.application.QueryExecutorBase
import com.intellij.psi.search.searches.DefinitionsScopedSearch
import com.intellij.util.Processor
import org.editorconfig.language.codeinsight.linemarker.EditorConfigOverriddenHeaderFinder
import org.editorconfig.language.psi.EditorConfigHeader

class EditorConfigHeaderImplementationSearch : QueryExecutorBase<EditorConfigHeader, DefinitionsScopedSearch.SearchParameters>(true) {
  override fun processQuery(queryParameters: DefinitionsScopedSearch.SearchParameters, consumer: Processor<in EditorConfigHeader>) {
    val header = queryParameters.element as? EditorConfigHeader ?: return
    EditorConfigOverriddenHeaderFinder().getMatchingHeaders(header).forEach {
      if (!consumer.process(it)) return
    }
  }
}
