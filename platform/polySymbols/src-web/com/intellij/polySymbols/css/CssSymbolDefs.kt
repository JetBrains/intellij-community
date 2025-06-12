// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("CssSymbolKinds")

package com.intellij.polySymbols.css

import com.intellij.polySymbols.PolySymbolProperty
import com.intellij.polySymbols.PolySymbolQualifiedKind

const val NAMESPACE_CSS: String = "css"

@JvmField
val CSS_PROPERTIES: PolySymbolQualifiedKind = PolySymbolQualifiedKind[NAMESPACE_CSS, "properties"]

@JvmField
val CSS_PSEUDO_ELEMENTS: PolySymbolQualifiedKind = PolySymbolQualifiedKind[NAMESPACE_CSS, "pseudo-elements"]

@JvmField
val CSS_PSEUDO_CLASSES: PolySymbolQualifiedKind = PolySymbolQualifiedKind[NAMESPACE_CSS, "pseudo-classes"]

@JvmField
val CSS_FUNCTIONS: PolySymbolQualifiedKind = PolySymbolQualifiedKind[NAMESPACE_CSS, "functions"]

@JvmField
val CSS_CLASSES: PolySymbolQualifiedKind = PolySymbolQualifiedKind[NAMESPACE_CSS, "classes"]

@JvmField
val CSS_PARTS: PolySymbolQualifiedKind = PolySymbolQualifiedKind[NAMESPACE_CSS, "parts"]

/**
 * Name of boolean property used by `css/pseudo-elements` and `css/pseudo-classes` symbols
 * to specify whether they require arguments. Defaults to false.
 **/
@JvmField
val PROP_CSS_ARGUMENTS: PolySymbolProperty<Any> = PolySymbolProperty.Companion["arguments"]