// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.editorconfig.language.codeinsight.completion.providers

import com.intellij.codeInsight.completion.CompletionParameters
import com.intellij.codeInsight.completion.CompletionResultSet
import com.intellij.codeInsight.template.impl.LiveTemplateLookupElementImpl
import com.intellij.patterns.PsiElementPattern
import com.intellij.psi.PsiElement
import com.intellij.psi.util.parentOfType
import com.intellij.util.ProcessingContext
import org.editorconfig.language.psi.EditorConfigSection
import org.editorconfig.language.services.EditorConfigOptionDescriptorManager
import org.editorconfig.language.util.EditorConfigTemplateUtil

object EditorConfigComplexKeyTemplateCompletionProvider : EditorConfigCompletionProviderBase() {
  override val destination: PsiElementPattern.Capture<PsiElement>
    get() = EditorConfigSimpleOptionKeyCompletionProvider.destination

  override fun addCompletions(parameters: CompletionParameters, context: ProcessingContext, result: CompletionResultSet) {
    val section = parameters.position.parentOfType<EditorConfigSection>(withSelf = true) ?: return
    val descriptors = EditorConfigOptionDescriptorManager.getInstance(parameters.originalFile.project).getQualifiedKeyDescriptors(true)

    EditorConfigTemplateUtil
      .buildSameStartClasses(descriptors)
      .filter { (_, sameStartClass) -> EditorConfigTemplateUtil.checkStructuralEquality(sameStartClass) }
      .map { (string, list) -> EditorConfigTemplateUtil.buildTemplate(string, list, section, initialNewLine = false) }
      .map { LiveTemplateLookupElementImpl(it, true) }
      .forEach(result::addElement)
  }
}
