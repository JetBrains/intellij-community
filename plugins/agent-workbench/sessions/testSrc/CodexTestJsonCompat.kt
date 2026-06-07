// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.sessions

import tools.jackson.core.JsonGenerator

internal fun JsonGenerator.writeFieldName(name: String) {
  writeName(name)
}

internal fun JsonGenerator.writeObjectFieldStart(name: String) {
  writeObjectPropertyStart(name)
}

internal fun JsonGenerator.writeArrayFieldStart(name: String) {
  writeArrayPropertyStart(name)
}

internal fun JsonGenerator.writeStringField(name: String, value: String?) {
  writeStringProperty(name, value)
}

internal fun JsonGenerator.writeBooleanField(name: String, value: Boolean) {
  writeBooleanProperty(name, value)
}

internal fun JsonGenerator.writeNumberField(name: String, value: Int) {
  writeNumberProperty(name, value)
}

internal fun JsonGenerator.writeNumberField(name: String, value: Long) {
  writeNumberProperty(name, value)
}

internal fun JsonGenerator.writeNullField(name: String) {
  writeNullProperty(name)
}
