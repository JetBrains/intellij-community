// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("JsSymbolKinds")

package com.intellij.polySymbols.js

import com.intellij.polySymbols.PolySymbolProperty
import com.intellij.polySymbols.PolySymbolQualifiedKind

const val NAMESPACE_JS: String = "js"

@JvmField
val JS_EVENTS: PolySymbolQualifiedKind = PolySymbolQualifiedKind[NAMESPACE_JS, "events"]

@JvmField
val JS_PROPERTIES: PolySymbolQualifiedKind = PolySymbolQualifiedKind[NAMESPACE_JS, "properties"]

@JvmField
val JS_KEYWORDS: PolySymbolQualifiedKind = PolySymbolQualifiedKind[NAMESPACE_JS, "keywords"]

@JvmField
val JS_SYMBOLS: PolySymbolQualifiedKind = PolySymbolQualifiedKind[NAMESPACE_JS, "symbols"]

@JvmField
val JS_STRING_LITERALS: PolySymbolQualifiedKind = PolySymbolQualifiedKind[NAMESPACE_JS, "string-literals"]

/**
 * One of [JsSymbolSymbolKind] enum values. By default, JS symbol is treated as [JsSymbolSymbolKind.Variable].
 **/
@JvmField
val PROP_JS_SYMBOL_KIND: PolySymbolProperty<JsSymbolSymbolKind> = PolySymbolProperty.Companion["js-symbol-kind"]
