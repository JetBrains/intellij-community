// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.editorconfig.language.codeinsight.completion.providers

import com.intellij.codeInsight.completion.CompletionParameters
import com.intellij.codeInsight.completion.CompletionResultSet
import com.intellij.editorconfig.common.syntax.psi.EditorConfigElementTypes
import com.intellij.editorconfig.common.syntax.psi.EditorConfigOptionValueIdentifier
import com.intellij.patterns.PlatformPatterns.psiElement
import com.intellij.patterns.PsiElementPattern
import com.intellij.psi.PsiElement
import com.intellij.util.ProcessingContext
import org.editorconfig.language.codeinsight.completion.visitors.EditorConfigValueCompletionCollectorInitiator
import org.editorconfig.language.schema.descriptors.getDescriptor

object EditorConfigComplexValueCompletionProvider : EditorConfigCompletionProviderBase() {
  override val destination: PsiElementPattern.Capture<PsiElement> =
    psiElement(EditorConfigElementTypes.IDENTIFIER)
      .withParent(psiElement(EditorConfigElementTypes.OPTION_VALUE_IDENTIFIER))

  override fun addCompletions(parameters: CompletionParameters, context: ProcessingContext, result: CompletionResultSet) {
    val position = parameters.position
    val identifier = position.parent as EditorConfigOptionValueIdentifier
    val parent = identifier.describableParent ?: return
    val file = position.containingFile

    val visitor = EditorConfigValueCompletionCollectorInitiator(parent, result, file)
    val parentDescriptor = parent.getDescriptor(true) ?: return
    parentDescriptor.accept(visitor)
  }
}
