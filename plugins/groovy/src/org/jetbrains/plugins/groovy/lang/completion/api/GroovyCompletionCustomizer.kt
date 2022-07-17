// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.lang.completion.api

import com.intellij.codeInsight.completion.CompletionParameters
import com.intellij.codeInsight.completion.CompletionResultSet
import com.intellij.psi.PsiFile
import org.jetbrains.annotations.ApiStatus

/**
 * Allows to tune completions for the plugins of Groovy language.
 * See its implementor in Gradle for typical usage.
 */
@ApiStatus.Experimental
interface GroovyCompletionCustomizer {

  fun customizeCompletionConsumer(completionParameters: CompletionParameters, resultSet: CompletionResultSet): CompletionResultSet

  fun generateCompletionConsumer(file: PsiFile, resultSet: CompletionResultSet): GroovyCompletionConsumer?
}