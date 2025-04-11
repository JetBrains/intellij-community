// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.editorconfig.language.schema.parser.handlers.impl

import com.google.gson.JsonObject
import org.editorconfig.language.schema.descriptors.EditorConfigDescriptor
import org.editorconfig.language.schema.descriptors.impl.EditorConfigQualifiedKeyDescriptor
import org.editorconfig.language.schema.parser.EditorConfigJsonSchemaConstants.DEPRECATION
import org.editorconfig.language.schema.parser.EditorConfigJsonSchemaConstants.DOCUMENTATION
import org.editorconfig.language.schema.parser.EditorConfigJsonSchemaConstants.LIST
import org.editorconfig.language.schema.parser.EditorConfigJsonSchemaConstants.OPTION
import org.editorconfig.language.schema.parser.EditorConfigJsonSchemaConstants.PAIR
import org.editorconfig.language.schema.parser.EditorConfigJsonSchemaConstants.QUALIFIED
import org.editorconfig.language.schema.parser.EditorConfigJsonSchemaConstants.TYPE
import org.editorconfig.language.schema.parser.EditorConfigJsonSchemaConstants.VALUES
import org.editorconfig.language.schema.parser.EditorConfigJsonSchemaException
import org.editorconfig.language.schema.parser.EditorConfigJsonSchemaParser
import org.editorconfig.language.schema.parser.handlers.EditorConfigDescriptorParseHandlerBase

class EditorConfigQualifiedOptionKeyDescriptorParseHandler : EditorConfigDescriptorParseHandlerBase() {
  override val requiredKeys: List<String> = listOf(TYPE, VALUES)
  override val forbiddenChildren: List<String> = listOf(PAIR, LIST, OPTION, QUALIFIED)

  override fun doHandle(jsonObject: JsonObject, parser: EditorConfigJsonSchemaParser): EditorConfigDescriptor {
    val rawValues = jsonObject[VALUES]
    if (!rawValues.isJsonArray) {
      throw EditorConfigJsonSchemaException(jsonObject)
    }

    val values = rawValues.asJsonArray.map(parser::parse)
    val documentation = tryGetString(jsonObject, DOCUMENTATION)
    val deprecation = tryGetString(jsonObject, DEPRECATION)
    return EditorConfigQualifiedKeyDescriptor(values, documentation, deprecation)
  }
}
