// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.service.completion

import com.intellij.codeInsight.completion.CompletionParameters
import com.intellij.codeInsight.completion.CompletionResultSet
import com.intellij.codeInsight.completion.CompletionSorter
import com.intellij.psi.PsiFile
import org.jetbrains.plugins.gradle.util.GradleConstants
import org.jetbrains.plugins.groovy.lang.completion.api.GroovyCompletionConsumer
import org.jetbrains.plugins.groovy.lang.completion.api.GroovyCompletionCustomizer
import org.jetbrains.plugins.groovy.lang.completion.impl.AccumulatingGroovyCompletionConsumer

class GradleCompletionCustomizer : GroovyCompletionCustomizer {

  override fun customizeCompletionConsumer(completionParameters: CompletionParameters,
                                           resultSet: CompletionResultSet): CompletionResultSet {
    val sorter = CompletionSorter.defaultSorter(completionParameters, resultSet.prefixMatcher)
    return resultSet.withRelevanceSorter(sorter.weighBefore("templates", GradleLookupWeigher()))
  }

  override fun generateCompletionConsumer(file: PsiFile, resultSet: CompletionResultSet): GroovyCompletionConsumer? {
    if (file.name.endsWith(GradleConstants.EXTENSION)) {
      return AccumulatingGroovyCompletionConsumer(resultSet)
    }
    else {
      return null
    }
  }
}