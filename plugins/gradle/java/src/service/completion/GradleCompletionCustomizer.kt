// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.service.completion

import com.intellij.codeInsight.completion.CompletionParameters
import com.intellij.codeInsight.completion.CompletionResultSet
import com.intellij.codeInsight.completion.CompletionSorter
import com.intellij.psi.PsiElement
import org.jetbrains.plugins.gradle.config.GradleFileType
import org.jetbrains.plugins.gradle.util.GradleConstants
import org.jetbrains.plugins.groovy.GroovyFileType
import org.jetbrains.plugins.groovy.lang.completion.api.GroovyCompletionConsumer
import org.jetbrains.plugins.groovy.lang.completion.api.GroovyCompletionCustomizer
import org.jetbrains.plugins.groovy.lang.completion.impl.AccumulatingGroovyCompletionConsumer

class GradleCompletionCustomizer : GroovyCompletionCustomizer {

  override fun customizeCompletionConsumer(completionParameters: CompletionParameters,
                                           resultSet: CompletionResultSet): CompletionResultSet {
    if (completionParameters.originalFile.fileType == GradleFileType || completionParameters.originalFile.fileType == GroovyFileType.GROOVY_FILE_TYPE && completionParameters.originalFile.virtualFile.extension == GradleConstants.EXTENSION) {
      val sorter = CompletionSorter.defaultSorter(completionParameters, resultSet.prefixMatcher)
      return resultSet.withRelevanceSorter(sorter.weighBefore("priority", GradleLookupWeigher()))
    } else {
      return resultSet
    }
  }

  override fun generateCompletionConsumer(element: PsiElement, resultSet: CompletionResultSet): GroovyCompletionConsumer? {
    val file = element.containingFile
    if (file.name.endsWith(GradleConstants.EXTENSION)) {
      return GradleCompletionConsumer(element, AccumulatingGroovyCompletionConsumer(resultSet))
    }
    else {
      return null
    }
  }
}