// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("JsSymbolKinds")

package com.intellij.polySymbols.js

import com.intellij.polySymbols.PolySymbolKind
import com.intellij.polySymbols.PolySymbolProperty

const val NAMESPACE_JS: String = "js"

@JvmField
val JS_EVENTS: PolySymbolKind = PolySymbolKind[NAMESPACE_JS, "events"]

@JvmField
val JS_PROPERTIES: PolySymbolKind = PolySymbolKind[NAMESPACE_JS, "properties"]

@JvmField
val JS_KEYWORDS: PolySymbolKind = PolySymbolKind[NAMESPACE_JS, "keywords"]

@JvmField
val JS_SYMBOLS: PolySymbolKind = PolySymbolKind[NAMESPACE_JS, "symbols"]

@JvmField
val JS_STRING_LITERALS: PolySymbolKind = PolySymbolKind[NAMESPACE_JS, "string-literals"]

/**
 * One of [JsSymbolSymbolKind] enum values. By default, JS symbol is treated as [JsSymbolSymbolKind.Variable].
 **/
object JsSymbolKindProperty : PolySymbolProperty<JsSymbolSymbolKind>("js-symbol-kind", JsSymbolSymbolKind::class.java)
