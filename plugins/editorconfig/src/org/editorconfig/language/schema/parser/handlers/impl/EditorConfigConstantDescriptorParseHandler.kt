// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.editorconfig.language.schema.parser.handlers.impl

import com.google.gson.JsonObject
import org.editorconfig.language.schema.descriptors.EditorConfigDescriptor
import org.editorconfig.language.schema.descriptors.impl.EditorConfigConstantDescriptor
import org.editorconfig.language.schema.parser.EditorConfigJsonSchemaConstants.DEPRECATION
import org.editorconfig.language.schema.parser.EditorConfigJsonSchemaConstants.DOCUMENTATION
import org.editorconfig.language.schema.parser.EditorConfigJsonSchemaConstants.TYPE
import org.editorconfig.language.schema.parser.EditorConfigJsonSchemaConstants.VALUE
import org.editorconfig.language.schema.parser.EditorConfigJsonSchemaException
import org.editorconfig.language.schema.parser.EditorConfigJsonSchemaParser
import org.editorconfig.language.schema.parser.handlers.EditorConfigDescriptorParseHandlerBase
import java.util.*

class EditorConfigConstantDescriptorParseHandler : EditorConfigDescriptorParseHandlerBase() {
  override val requiredKeys = listOf(TYPE, VALUE)

  override fun doHandle(jsonObject: JsonObject, parser: EditorConfigJsonSchemaParser): EditorConfigDescriptor {
    val value = jsonObject[VALUE]
    if (!value.isJsonPrimitive) {
      throw EditorConfigJsonSchemaException(jsonObject)
    }

    val text = value.asString.lowercase(Locale.ROOT)
    val documentation = tryGetString(jsonObject, DOCUMENTATION)
    val deprecation = tryGetString(jsonObject, DEPRECATION)
    return EditorConfigConstantDescriptor(text, documentation, deprecation)
  }
}
