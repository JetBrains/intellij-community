// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.editorconfig.language.codeinsight.completion.templates

import com.intellij.codeInsight.template.impl.ConstantNode
import com.intellij.codeInsight.template.impl.MacroCallNode
import com.intellij.codeInsight.template.impl.TemplateImpl
import com.intellij.codeInsight.template.impl.Variable
import com.intellij.codeInsight.template.macro.CompleteMacro
import com.intellij.editorconfig.common.syntax.psi.EditorConfigDescribableElement
import com.intellij.editorconfig.common.syntax.psi.EditorConfigSection
import org.editorconfig.language.codeinsight.completion.getSeparatorInFile
import org.editorconfig.language.schema.descriptors.EditorConfigDescriptor
import org.editorconfig.language.schema.descriptors.impl.EditorConfigConstantDescriptor
import org.editorconfig.language.schema.descriptors.impl.EditorConfigDeclarationDescriptor
import org.editorconfig.language.schema.descriptors.impl.EditorConfigQualifiedKeyDescriptor
import org.editorconfig.language.schema.descriptors.impl.EditorConfigUnionDescriptor
import org.editorconfig.language.util.EditorConfigIdentifierUtil
import org.editorconfig.language.util.EditorConfigTemplateUtil

class EditorConfigTemplateLineBuildAssistant(
  private val template: TemplateImpl,
  private val section: EditorConfigSection,
  private val predefinedVariables: Map<String, String>,
  private val cachedVariables: MutableMap<String, Variable>
) {
  private val assistant = EditorConfigTemplateSegmentBuildAssistant(template, cachedVariables)
  fun constructLine(group: List<EditorConfigQualifiedKeyDescriptor>) {

    fun add(descriptor: EditorConfigDescriptor): Unit = when (descriptor) {
      is EditorConfigConstantDescriptor -> assistant.addNextConstant(descriptor.text)
      is EditorConfigUnionDescriptor -> descriptor.children.forEach(::add)
      is EditorConfigDeclarationDescriptor -> {
        val id = descriptor.id
        val predefinedVariable = predefinedVariables[id]
        val cachedVariable = cachedVariables[id]
        when {
          predefinedVariable != null -> assistant.addNextConstant(predefinedVariable)
          cachedVariable != null -> template.addVariableSegment(cachedVariable.name)
          else -> {
            assistant.saveLastVariableId(id)
            EditorConfigIdentifierUtil
              .findIdentifiers(section, id)
              .map(EditorConfigDescribableElement::getText)
              .forEach(assistant::addNextConstant)
          }
        }
      }
      else -> throw IllegalStateException()
    }

    val children = group.firstOrNull()?.children ?: return
    for (childIndex in 0 until children.size - 1) {
      group.forEach { add(it.children[childIndex]) }
      assistant.saveNextTokens()
      template.addTextSegment(".")
    }

    group.forEach { add(it.children[children.size - 1]) }
    assistant.saveNextTokens()
    template.addTextSegment(getSeparatorInFile(section.containingFile))
    template.addVariable(
      EditorConfigTemplateUtil.uniqueId,
      MacroCallNode(CompleteMacro()),
      ConstantNode("value").withLookupStrings("value"),
      true
    )
  }
}
