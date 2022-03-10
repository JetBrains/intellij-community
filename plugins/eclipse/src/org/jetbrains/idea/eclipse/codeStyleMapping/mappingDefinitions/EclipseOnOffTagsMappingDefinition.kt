// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.eclipse.codeStyleMapping.mappingDefinitions

import org.jetbrains.idea.eclipse.codeStyleMapping.EclipseJavaCodeStyleMappingDefinitionBuilder
import org.jetbrains.idea.eclipse.codeStyleMapping.util.SettingsMappingHelpers.field
import org.jetbrains.idea.eclipse.codeStyleMapping.util.SettingsMappingHelpers.compute
import org.jetbrains.idea.eclipse.codeStyleMapping.util.convertBoolean

internal fun EclipseJavaCodeStyleMappingDefinitionBuilder.addOnOffTagsMapping() {
  onImportDo {
    general.FORMATTER_TAGS_ACCEPT_REGEXP = false
  }
  "use_on_off_tags" mapTo
    compute(
      import = { value -> general.FORMATTER_TAGS_ENABLED = value },
      export = { !general.FORMATTER_TAGS_ACCEPT_REGEXP && general.FORMATTER_TAGS_ENABLED }
    ).convertBoolean()
  "enabling_tag" mapTo
    field(general::FORMATTER_ON_TAG)
  "disabling_tag" mapTo
    field(general::FORMATTER_OFF_TAG)
}