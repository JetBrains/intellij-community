// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.eclipse.codeStyleMapping.mappingDefinitions

import org.jetbrains.idea.eclipse.codeStyleMapping.EclipseJavaCodeStyleMappingDefinitionBuilder
import org.jetbrains.idea.eclipse.codeStyleMapping.util.SettingsMappingHelpers.const
import org.jetbrains.idea.eclipse.codeStyleMapping.util.SettingsMappingHelpers.field
import org.jetbrains.idea.eclipse.codeStyleMapping.util.SettingsMappingHelpers.compute
import org.jetbrains.idea.eclipse.codeStyleMapping.util.convertBoolean
import org.jetbrains.idea.eclipse.codeStyleMapping.util.doNotImport
import org.jetbrains.idea.eclipse.codeStyleMapping.valueConversions.convertBracePosition
import org.jetbrains.idea.eclipse.importer.EclipseFormatterOptions.VALUE_END_OF_LINE

internal fun EclipseJavaCodeStyleMappingDefinitionBuilder.addBracePositionsMapping() {
  "brace_position_for_type_declaration" mapTo
    field(common::CLASS_BRACE_STYLE)
      .convertBracePosition()
  "brace_position_for_anonymous_type_declaration" mapTo
    field(common::CLASS_BRACE_STYLE)
      .doNotImport()
      .convertBracePosition()
  "brace_position_for_constructor_declaration" mapTo
    field(common::METHOD_BRACE_STYLE)
      .doNotImport()
      .convertBracePosition()
  "brace_position_for_method_declaration" mapTo
    field(common::METHOD_BRACE_STYLE)
      .convertBracePosition()
  "brace_position_for_enum_declaration" mapTo
    field(common::CLASS_BRACE_STYLE)
      .doNotImport()
      .convertBracePosition()
  "brace_position_for_enum_constant" mapTo
    field(common::BRACE_STYLE)
      .doNotImport()
      .convertBracePosition()
  "brace_position_for_record_declaration" mapTo
    field(common::CLASS_BRACE_STYLE)
      .doNotImport()
      .convertBracePosition()
  "brace_position_for_record_constructor" mapTo
    field(common::METHOD_BRACE_STYLE)
      .doNotImport()
      .convertBracePosition()
  "brace_position_for_annotation_type_declaration" mapTo
    field(common::CLASS_BRACE_STYLE)
      .doNotImport()
      .convertBracePosition()
  "brace_position_for_block" mapTo
    field(common::BRACE_STYLE)
      .convertBracePosition()
  "brace_position_for_block_in_case" mapTo
    field(common::BRACE_STYLE)
      .doNotImport()
      .convertBracePosition()
  "brace_position_for_switch" mapTo
    field(common::BRACE_STYLE)
      .doNotImport()
      .convertBracePosition()
  "brace_position_for_array_initializer" mapTo
    compute(
      import = { _ ->
        common.ARRAY_INITIALIZER_LBRACE_ON_NEXT_LINE = false
        common.ARRAY_INITIALIZER_RBRACE_ON_NEXT_LINE = false
      },
      export = { VALUE_END_OF_LINE }
    )
  "keep_empty_array_initializer_on_one_line" mapTo
    const(true)
      .convertBoolean()
  "brace_position_for_lambda_body" mapTo
    field(common::LAMBDA_BRACE_STYLE)
      .convertBracePosition()
}
