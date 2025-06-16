// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("HtmlSymbolKinds")

package com.intellij.polySymbols.html

import com.intellij.polySymbols.PolySymbol
import com.intellij.polySymbols.PolySymbolProperty
import com.intellij.polySymbols.PolySymbolQualifiedKind
import com.intellij.polySymbols.query.impl.PolySymbolMatchBase
import com.intellij.polySymbols.utils.merge

const val NAMESPACE_HTML: String = "html"

@JvmField
val HTML_ELEMENTS: PolySymbolQualifiedKind = PolySymbolQualifiedKind.Companion[NAMESPACE_HTML, "elements"]

@JvmField
val HTML_ATTRIBUTES: PolySymbolQualifiedKind = PolySymbolQualifiedKind.Companion[NAMESPACE_HTML, "attributes"]

@JvmField
val HTML_ATTRIBUTE_VALUES: PolySymbolQualifiedKind = PolySymbolQualifiedKind.Companion[NAMESPACE_HTML, "values"]

@JvmField
val HTML_SLOTS: PolySymbolQualifiedKind = PolySymbolQualifiedKind.Companion[NAMESPACE_HTML, "slots"]

val PolySymbol.htmlAttributeValue: PolySymbolHtmlAttributeValue?
  get() = if (this is PolySymbolMatchBase)
    this.reversedSegments().flatMap { it.symbols }.map { it[PROP_HTML_ATTRIBUTE_VALUE] }.merge()
  else
    this[PROP_HTML_ATTRIBUTE_VALUE]

/**
 * A special property to support symbols representing HTML attributes.
 **/
@JvmField
val PROP_HTML_ATTRIBUTE_VALUE: PolySymbolProperty<PolySymbolHtmlAttributeValue> = PolySymbolProperty.Companion["html-attribute-value"]