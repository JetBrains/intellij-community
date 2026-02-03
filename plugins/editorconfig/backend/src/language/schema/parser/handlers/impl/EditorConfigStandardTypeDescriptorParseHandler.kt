// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.editorconfig.language.schema.parser.handlers.impl

import com.google.gson.JsonObject
import org.editorconfig.language.schema.descriptors.EditorConfigDescriptor
import org.editorconfig.language.schema.descriptors.impl.EditorConfigNumberDescriptor
import org.editorconfig.language.schema.descriptors.impl.EditorConfigStringDescriptor
import org.editorconfig.language.schema.parser.EditorConfigJsonSchemaConstants
import org.editorconfig.language.schema.parser.EditorConfigJsonSchemaConstants.DOCUMENTATION
import org.editorconfig.language.schema.parser.EditorConfigJsonSchemaConstants.NUMBER
import org.editorconfig.language.schema.parser.EditorConfigJsonSchemaConstants.STRING
import org.editorconfig.language.schema.parser.EditorConfigJsonSchemaConstants.TEXT
import org.editorconfig.language.schema.parser.EditorConfigJsonSchemaConstants.TYPE
import org.editorconfig.language.schema.parser.EditorConfigJsonSchemaException
import org.editorconfig.language.schema.parser.EditorConfigJsonSchemaParser
import org.editorconfig.language.schema.parser.handlers.EditorConfigDescriptorParseHandlerBase

class EditorConfigStandardTypeDescriptorParseHandler : EditorConfigDescriptorParseHandlerBase() {
  override val requiredKeys: List<String> = listOf(TYPE)

  override fun doHandle(jsonObject: JsonObject, parser: EditorConfigJsonSchemaParser): EditorConfigDescriptor {
    val documentation = tryGetString(jsonObject, DOCUMENTATION)
    val deprecation = tryGetString(jsonObject, EditorConfigJsonSchemaConstants.DEPRECATION)
    return when (jsonObject[TYPE].asString) {
      NUMBER -> EditorConfigNumberDescriptor(documentation, deprecation)
      STRING -> EditorConfigStringDescriptor(documentation, deprecation)
      TEXT -> EditorConfigStringDescriptor(documentation, deprecation, ".*")
      else -> throw EditorConfigJsonSchemaException(jsonObject)
    }
  }
}
