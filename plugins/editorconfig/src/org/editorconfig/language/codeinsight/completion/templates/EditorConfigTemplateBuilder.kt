// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.editorconfig.language.codeinsight.completion.templates

import com.intellij.codeInsight.template.impl.TemplateImpl
import org.editorconfig.language.psi.EditorConfigSection
import org.editorconfig.language.schema.descriptors.impl.EditorConfigQualifiedKeyDescriptor

/**
 * Creates template based on given 2d list of descriptors
 * Every descriptorGroup is merged into single template for option
 */
class EditorConfigTemplateBuilder(
  private val key: String,
  private val section: EditorConfigSection,
  private val predefinedVariables: Map<String, String> = emptyMap(),
  private val initialNewLine: Boolean = true
) {
  private val descriptorGroups = mutableListOf<List<EditorConfigQualifiedKeyDescriptor>>()
  fun appendDescriptorGroup(group: List<EditorConfigQualifiedKeyDescriptor>): EditorConfigTemplateBuilder {
    descriptorGroups.add(group)
    return this
  }

  fun build(): TemplateImpl {
    val template = TemplateImpl(key, "EditorConfig")
    val assistant = EditorConfigTemplateLineBuildAssistant(template, section, predefinedVariables, mutableMapOf())
    descriptorGroups.forEachIndexed { index, group ->
      if (initialNewLine || index != 0) template.addTextSegment("\n")
      assistant.constructLine(group)
    }
    return template
  }
}
