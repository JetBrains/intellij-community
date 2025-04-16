// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.editorconfig.language.codeinsight.completion.providers

import com.intellij.codeInsight.completion.CompletionParameters
import com.intellij.codeInsight.completion.CompletionResultSet
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.editorconfig.common.syntax.psi.EditorConfigPsiFile
import com.intellij.patterns.PlatformPatterns.psiElement
import com.intellij.patterns.PsiElementPattern
import com.intellij.psi.PsiElement
import com.intellij.util.ProcessingContext
import org.editorconfig.language.filetype.EditorConfigFileConstants
import org.editorconfig.language.util.hasValidRootDeclaration

object EditorConfigRootDeclarationCompletionProvider : EditorConfigCompletionProviderBase() {
  override val destination: PsiElementPattern.Capture<PsiElement> =
    psiElement().withParent(EditorConfigPsiFile::class.java)

  override fun addCompletions(parameters: CompletionParameters, context: ProcessingContext, result: CompletionResultSet) {
    val file = parameters.originalFile as? EditorConfigPsiFile ?: return
    if (file.hasValidRootDeclaration) return
    val rootDeclaration = EditorConfigFileConstants.getRootDeclarationFor(parameters.position.containingFile)
    val element = LookupElementBuilder.create(rootDeclaration)
    result.addElement(element)
  }
}
