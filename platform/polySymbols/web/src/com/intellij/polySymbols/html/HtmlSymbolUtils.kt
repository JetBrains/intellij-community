// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("HtmlSymbolUtils")

package com.intellij.polySymbols.html

fun Sequence<PolySymbolHtmlAttributeValue?>.merge(): PolySymbolHtmlAttributeValue? {
  var kind: PolySymbolHtmlAttributeValue.Kind? = null
  var type: PolySymbolHtmlAttributeValue.Type? = null
  var required: Boolean? = null
  var default: String? = null
  var langType: Any? = null

  for (value in this) {
    if (value == null) continue
    if (kind == null || kind == PolySymbolHtmlAttributeValue.Kind.PLAIN) {
      kind = value.kind
    }
    if (type == null) {
      type = value.type
    }
    if (required == null) {
      required = value.required
    }
    if (default == null) {
      default = value.default
    }
    if (langType == null) {
      langType = value.langType
    }
  }
  return if (kind != null
             || type != null
             || required != null
             || langType != null
             || default != null)
    PolySymbolHtmlAttributeValue.create(kind, type, required, default, langType)
  else null
}