// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.editorconfig.language.codeinsight.completion.providers

import com.intellij.codeInsight.completion.CompletionParameters
import com.intellij.codeInsight.completion.CompletionResultSet
import com.intellij.codeInsight.template.impl.LiveTemplateLookupElementImpl
import com.intellij.patterns.PsiElementPattern
import com.intellij.psi.PsiElement
import com.intellij.util.ProcessingContext
import org.editorconfig.language.messages.EditorConfigBundle
import org.editorconfig.language.psi.EditorConfigSection
import org.editorconfig.language.schema.descriptors.EditorConfigDescriptor
import org.editorconfig.language.schema.descriptors.impl.EditorConfigConstantDescriptor
import org.editorconfig.language.schema.descriptors.impl.EditorConfigDeclarationDescriptor
import org.editorconfig.language.schema.descriptors.impl.EditorConfigQualifiedKeyDescriptor
import org.editorconfig.language.schema.descriptors.impl.EditorConfigUnionDescriptor
import org.editorconfig.language.services.EditorConfigOptionDescriptorManager
import org.editorconfig.language.util.EditorConfigPsiTreeUtil.getParentOfType
import org.editorconfig.language.util.EditorConfigTemplateUtil

object EditorConfigComplexKeyFullTemplateCompletionProvider : EditorConfigCompletionProviderBase() {
  override val destination: PsiElementPattern.Capture<PsiElement>
    get() = EditorConfigComplexKeyTemplateCompletionProvider.destination

  override fun addCompletions(parameters: CompletionParameters, context: ProcessingContext, result: CompletionResultSet) {
    val section = parameters.position.getParentOfType<EditorConfigSection>() ?: return
    val descriptors = EditorConfigOptionDescriptorManager.getInstance(parameters.originalFile.project).getQualifiedKeyDescriptors(true)

    EditorConfigTemplateUtil
      .buildSameStartClasses(descriptors)
      .filter { EditorConfigTemplateUtil.checkStructuralEquality(it.value) }
      .mapKeys { EditorConfigBundle.get("completion.all.required.declarations", it.key) }
      .map { it.key to it.value.filter(::isRequired) }
      .filter { it.second.size > 1 }
      .map { EditorConfigTemplateUtil.buildFullTemplate(it.first, it.second, section, initialNewLine = false) }
      .map { LiveTemplateLookupElementImpl(it, true) }
      .forEach(result::addElement)
  }

  private fun isRequired(descriptor: EditorConfigDescriptor): Boolean = when (descriptor) {
    is EditorConfigQualifiedKeyDescriptor -> descriptor.children.any(::isRequired)
    is EditorConfigConstantDescriptor -> false
    is EditorConfigDeclarationDescriptor -> descriptor.isRequired
    is EditorConfigUnionDescriptor -> descriptor.children.any(::isRequired)
    else -> throw IllegalStateException()
  }
}
