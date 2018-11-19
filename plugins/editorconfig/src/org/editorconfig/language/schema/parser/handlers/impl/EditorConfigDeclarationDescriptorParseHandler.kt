// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.editorconfig.language.schema.parser.handlers.impl

import com.google.gson.JsonObject
import org.editorconfig.language.schema.descriptors.EditorConfigDescriptor
import org.editorconfig.language.schema.descriptors.impl.EditorConfigDeclarationDescriptor
import org.editorconfig.language.schema.parser.EditorConfigJsonSchemaConstants
import org.editorconfig.language.schema.parser.EditorConfigJsonSchemaConstants.DOCUMENTATION
import org.editorconfig.language.schema.parser.EditorConfigJsonSchemaConstants.ID
import org.editorconfig.language.schema.parser.EditorConfigJsonSchemaConstants.NEEDS_REFERENCES
import org.editorconfig.language.schema.parser.EditorConfigJsonSchemaConstants.REQUIRED
import org.editorconfig.language.schema.parser.EditorConfigJsonSchemaConstants.TYPE
import org.editorconfig.language.schema.parser.EditorConfigJsonSchemaException
import org.editorconfig.language.schema.parser.EditorConfigJsonSchemaParser
import org.editorconfig.language.schema.parser.handlers.EditorConfigDescriptorParseHandlerBase

class EditorConfigDeclarationDescriptorParseHandler : EditorConfigDescriptorParseHandlerBase() {
  override val requiredKeys = listOf(TYPE, ID)
  override val optionalKeys = super.optionalKeys + listOf(NEEDS_REFERENCES, REQUIRED)

  override fun doHandle(jsonObject: JsonObject, parser: EditorConfigJsonSchemaParser): EditorConfigDescriptor {
    val rawId = jsonObject[ID]
    if (!rawId.isJsonPrimitive) {
      throw EditorConfigJsonSchemaException(jsonObject)
    }
    val id = rawId.asString

    val documentation = tryGetString(jsonObject, DOCUMENTATION)
    val deprecation = tryGetString(jsonObject, EditorConfigJsonSchemaConstants.DEPRECATION)
    val needsReferences = tryGetBoolean(jsonObject, NEEDS_REFERENCES) ?: true
    val required = tryGetBoolean(jsonObject, REQUIRED) ?: false

    return EditorConfigDeclarationDescriptor(id, needsReferences, required, documentation, deprecation)
  }
}
