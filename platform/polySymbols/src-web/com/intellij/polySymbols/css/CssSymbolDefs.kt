// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("CssSymbolKinds")

package com.intellij.polySymbols.css

import com.intellij.polySymbols.PolySymbolKind
import com.intellij.polySymbols.PolySymbolProperty

const val NAMESPACE_CSS: String = "css"

@JvmField
val CSS_PROPERTIES: PolySymbolKind = PolySymbolKind[NAMESPACE_CSS, "properties"]

@JvmField
val CSS_PSEUDO_ELEMENTS: PolySymbolKind = PolySymbolKind[NAMESPACE_CSS, "pseudo-elements"]

@JvmField
val CSS_PSEUDO_CLASSES: PolySymbolKind = PolySymbolKind[NAMESPACE_CSS, "pseudo-classes"]

@JvmField
val CSS_FUNCTIONS: PolySymbolKind = PolySymbolKind[NAMESPACE_CSS, "functions"]

@JvmField
val CSS_CLASSES: PolySymbolKind = PolySymbolKind[NAMESPACE_CSS, "classes"]

@JvmField
val CSS_PARTS: PolySymbolKind = PolySymbolKind[NAMESPACE_CSS, "parts"]

/**
 * Name of boolean property used by `css/pseudo-elements` and `css/pseudo-classes` symbols
 * to specify whether they require arguments. Defaults to false.
 **/
@JvmField
val PROP_CSS_ARGUMENTS: PolySymbolProperty<Any> = PolySymbolProperty.Companion["arguments"]