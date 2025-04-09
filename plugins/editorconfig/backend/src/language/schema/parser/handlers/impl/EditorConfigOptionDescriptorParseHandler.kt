// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.editorconfig.language.schema.parser.handlers.impl

import com.google.gson.JsonObject
import org.editorconfig.language.schema.descriptors.EditorConfigDescriptor
import org.editorconfig.language.schema.descriptors.impl.EditorConfigOptionDescriptor
import org.editorconfig.language.schema.parser.EditorConfigJsonSchemaConstants
import org.editorconfig.language.schema.parser.EditorConfigJsonSchemaConstants.DOCUMENTATION
import org.editorconfig.language.schema.parser.EditorConfigJsonSchemaConstants.KEY
import org.editorconfig.language.schema.parser.EditorConfigJsonSchemaConstants.OPTION
import org.editorconfig.language.schema.parser.EditorConfigJsonSchemaConstants.TYPE
import org.editorconfig.language.schema.parser.EditorConfigJsonSchemaConstants.VALUE
import org.editorconfig.language.schema.parser.EditorConfigJsonSchemaParser
import org.editorconfig.language.schema.parser.handlers.EditorConfigDescriptorParseHandlerBase

class EditorConfigOptionDescriptorParseHandler : EditorConfigDescriptorParseHandlerBase() {
  override val requiredKeys: List<String> = listOf(TYPE, KEY, VALUE)
  override val forbiddenChildren = listOf(OPTION)

  override fun doHandle(jsonObject: JsonObject, parser: EditorConfigJsonSchemaParser): EditorConfigDescriptor {
    val key = parser.parse(jsonObject[KEY])
    val value = parser.parse(jsonObject[VALUE])
    val documentation = tryGetString(jsonObject, DOCUMENTATION)
    val deprecation = tryGetString(jsonObject, EditorConfigJsonSchemaConstants.DEPRECATION)
    return EditorConfigOptionDescriptor(key, value, documentation, deprecation)
  }
}
