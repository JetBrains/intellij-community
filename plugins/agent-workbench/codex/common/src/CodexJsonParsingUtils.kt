// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.codex.common

import com.fasterxml.jackson.core.JsonParser
import com.intellij.agent.workbench.json.readJsonLongOrNull
import com.intellij.agent.workbench.json.readJsonStringOrNull
import com.intellij.agent.workbench.json.forEachJsonObjectField as forEachWorkbenchJsonObjectField

inline fun forEachObjectField(parser: JsonParser, onField: (String) -> Boolean) {
  forEachWorkbenchJsonObjectField(parser, onField)
}

fun readStringOrNull(parser: JsonParser): String? {
  return readJsonStringOrNull(parser)
}

fun readLongOrNull(parser: JsonParser): Long? {
  return readJsonLongOrNull(parser)
}
