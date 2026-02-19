package com.intellij.cce.util

import com.intellij.cce.core.CodeElement
import com.intellij.cce.core.CodeFragment
import com.intellij.cce.core.CodeToken


fun CodeFragment.extractAdditionalProperties(): Set<Map<String, String>> {
  return this.getChildren().map { it.extractAdditionalProperties() }.toSet()
}

private fun CodeElement.extractAdditionalProperties(): Map<String, String> {
  if (this !is CodeToken) {
    return emptyMap()
  }
  return this.properties.additionalPropertyNames().associateWith {
    this.properties.additionalProperty(it)!!
  }
}