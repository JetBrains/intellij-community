// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl.multiverse

import com.intellij.codeInsight.multiverse.CodeInsightContext
import com.intellij.codeInsight.multiverse.CodeInsightContextPresentationProvider
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.project.Project
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Nls
import javax.swing.Icon


private val CODE_INSIGHT_CONTEXT_PRESENTATION_EP: ExtensionPointName<CodeInsightContextPresentationProvider<out CodeInsightContext>> = ExtensionPointName.create("com.intellij.multiverse.codeInsightContextPresentationProvider")

@ApiStatus.Experimental
data class CodeInsightContextPresentation(
  val context: CodeInsightContext,
  val text: @Nls String, // todo IJPL-339 capitalization???
  val icon: Icon?,
)

@ApiStatus.Experimental
fun createCodeInsightContextPresentation(codeInsightContext: CodeInsightContext, project: Project): CodeInsightContextPresentation {
  val (icon, presentableText) = CODE_INSIGHT_CONTEXT_PRESENTATION_EP.computeSafeIfAny { provider ->
    if (provider.isApplicable(codeInsightContext)) {
      provider as CodeInsightContextPresentationProvider<CodeInsightContext>

      val icon = provider.getIcon(codeInsightContext, project)
      val presentableText = provider.getPresentableText(codeInsightContext, project)

      icon to presentableText
    }
    else null
  } ?: (null to "")

  return CodeInsightContextPresentation(
    context = codeInsightContext,
    text = presentableText,
    icon = icon,
  )
}