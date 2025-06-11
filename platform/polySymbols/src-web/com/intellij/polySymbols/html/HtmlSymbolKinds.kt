// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("HtmlSymbolKinds")

package com.intellij.polySymbols.html

import com.intellij.polySymbols.PolySymbolQualifiedKind

const val NAMESPACE_HTML: String = "html"

@JvmField
val HTML_ELEMENTS: PolySymbolQualifiedKind = PolySymbolQualifiedKind.Companion[NAMESPACE_HTML, "elements"]

@JvmField
val HTML_ATTRIBUTES: PolySymbolQualifiedKind = PolySymbolQualifiedKind.Companion[NAMESPACE_HTML, "attributes"]

@JvmField
val HTML_ATTRIBUTE_VALUES: PolySymbolQualifiedKind = PolySymbolQualifiedKind.Companion[NAMESPACE_HTML, "values"]

@JvmField
val HTML_SLOTS: PolySymbolQualifiedKind = PolySymbolQualifiedKind.Companion[NAMESPACE_HTML, "slots"]