// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.editorconfig.language.schema.parser

import com.google.gson.JsonElement

/**
 * This exception notifies that schema provided to [EditorConfigJsonSchemaParser]
 * does not follow option description rules
 */
class EditorConfigJsonSchemaException(val element: JsonElement) : RuntimeException()
