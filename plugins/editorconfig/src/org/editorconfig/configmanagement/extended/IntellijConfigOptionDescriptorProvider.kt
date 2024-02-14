// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.editorconfig.configmanagement.extended

import com.intellij.application.options.CodeStyle
import com.intellij.application.options.codeStyle.properties.*
import com.intellij.openapi.project.Project
import org.editorconfig.Utils
import org.editorconfig.language.extensions.EditorConfigOptionDescriptorProvider
import org.editorconfig.language.schema.descriptors.EditorConfigDescriptor
import org.editorconfig.language.schema.descriptors.impl.*

private const val EXCEPT_NONE_REGEXP = "(^(?!none).*|.{4}.+)"

internal class IntellijConfigOptionDescriptorProvider : EditorConfigOptionDescriptorProvider {
  override fun getOptionDescriptors(project: Project): List<EditorConfigOptionDescriptor> =
    if (!Utils.isFullIntellijSettingsSupport()) emptyList() else getAllOptions()

  override fun requiresFullSupport(): Boolean = Utils.isFullIntellijSettingsSupport()

  private fun getAllOptions(): List<EditorConfigOptionDescriptor> {
    val mappers = buildList {
      CodeStylePropertiesUtil.collectMappers(CodeStyle.getDefaultSettings()) { add(it) }
    }
    return buildList {
      for (mapper in mappers) {
        for (property in mapper.enumProperties()) {
          val ecNames = getEditorConfigNames(mapper, property)
          if (ecNames.isEmpty()) continue
          val valueDescriptor = createValueDescriptor(property, mapper)
          if (valueDescriptor != null) {
            for (ecName in ecNames) {
              val descriptor = EditorConfigOptionDescriptor(
                EditorConfigConstantDescriptor(ecName, mapper.getPropertyDescription(property), null),
                valueDescriptor,
                null, null)
              add(descriptor)
            }
          }
        }
      }
    }
  }

  private fun getEditorConfigNames(mapper: AbstractCodeStylePropertyMapper, property: String): List<String> {
    if (EditorConfigIntellijNameUtil.isIndentProperty(property) && mapper !is GeneralCodeStylePropertyMapper) {
      // Create a special language indent property like ij_lang_indent_size
      return listOf(EditorConfigIntellijNameUtil.getLanguageProperty(mapper, property))
    }
    if (IntellijPropertyKindMap.getPropertyKind(property) == EditorConfigPropertyKind.EDITOR_CONFIG_STANDARD) {
      // Descriptions for other standard properties are added separately
      return emptyList()
    }
    return EditorConfigIntellijNameUtil.toEditorConfigNames(mapper, property)
  }

  private fun createValueDescriptor(property: String, mapper: AbstractCodeStylePropertyMapper): EditorConfigDescriptor? =
    when (val accessor = mapper.getAccessor(property)) {
      is CodeStyleChoiceList ->
        EditorConfigUnionDescriptor(choicesToDescriptorList(accessor as CodeStyleChoiceList), null, null)
      is IntegerAccessor ->
        EditorConfigNumberDescriptor(null, null)
      is ValueListPropertyAccessor<*> ->
        createListDescriptor(EditorConfigStringDescriptor(null, null, EXCEPT_NONE_REGEXP), accessor.isEmptyListAllowed)
      is ExternalStringAccessor<*> ->
        EditorConfigStringDescriptor(null, null, ".*")
      is VisualGuidesAccessor ->
        createListDescriptor(EditorConfigNumberDescriptor(null, null), true)
      else ->
        null
    }

  private fun createListDescriptor(childDescriptor: EditorConfigDescriptor, canBeEmpty: Boolean): EditorConfigDescriptor {
    val listDescriptor = EditorConfigListDescriptor(0, true, listOf(childDescriptor), null, null)
    return if (canBeEmpty) {
      EditorConfigUnionDescriptor(
        listOf(
          listDescriptor,
          EditorConfigConstantDescriptor(EditorConfigValueUtil.EMPTY_LIST_VALUE, null, null)
        ),
        null,
        null
      )
    }
    else {
      listDescriptor
    }
  }

  private fun choicesToDescriptorList(list: CodeStyleChoiceList): List<EditorConfigDescriptor> =
    list.choices.map { EditorConfigConstantDescriptor(it, null, null) }
}
