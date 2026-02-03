// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.editorconfig.language.schema.parser.handlers.impl

import com.google.gson.JsonObject
import org.editorconfig.language.schema.descriptors.EditorConfigDescriptor
import org.editorconfig.language.schema.descriptors.impl.EditorConfigPairDescriptor
import org.editorconfig.language.schema.parser.EditorConfigJsonSchemaConstants
import org.editorconfig.language.schema.parser.EditorConfigJsonSchemaConstants.DOCUMENTATION
import org.editorconfig.language.schema.parser.EditorConfigJsonSchemaConstants.FIRST
import org.editorconfig.language.schema.parser.EditorConfigJsonSchemaConstants.OPTION
import org.editorconfig.language.schema.parser.EditorConfigJsonSchemaConstants.PAIR
import org.editorconfig.language.schema.parser.EditorConfigJsonSchemaConstants.SECOND
import org.editorconfig.language.schema.parser.EditorConfigJsonSchemaConstants.TYPE
import org.editorconfig.language.schema.parser.EditorConfigJsonSchemaParser
import org.editorconfig.language.schema.parser.handlers.EditorConfigDescriptorParseHandlerBase

class EditorConfigPairDescriptorParseHandler : EditorConfigDescriptorParseHandlerBase() {
  override val requiredKeys: List<String> = listOf(TYPE, FIRST, SECOND)
  override val forbiddenChildren: List<String> = listOf(OPTION, PAIR)

  override fun doHandle(jsonObject: JsonObject, parser: EditorConfigJsonSchemaParser): EditorConfigDescriptor {
    val first = parser.parse(jsonObject[FIRST])
    val second = parser.parse(jsonObject[SECOND])
    val documentation = tryGetString(jsonObject, DOCUMENTATION)
    val deprecation = tryGetString(jsonObject, EditorConfigJsonSchemaConstants.DEPRECATION)
    return EditorConfigPairDescriptor(first, second, documentation, deprecation)
  }
}
