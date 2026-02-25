// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("HtmlSymbolKinds")

package com.intellij.polySymbols.html

import com.intellij.polySymbols.PolySymbol
import com.intellij.polySymbols.PolySymbolKind
import com.intellij.polySymbols.PolySymbolProperty
import com.intellij.polySymbols.query.impl.PolySymbolMatchBase
import com.intellij.polySymbols.utils.PolySymbolTypeSupport
import com.intellij.polySymbols.utils.merge
import com.intellij.psi.PsiElement

const val NAMESPACE_HTML: String = "html"

@JvmField
val HTML_ELEMENTS: PolySymbolKind = PolySymbolKind.Companion[NAMESPACE_HTML, "elements"]

@JvmField
val HTML_ATTRIBUTES: PolySymbolKind = PolySymbolKind.Companion[NAMESPACE_HTML, "attributes"]

@JvmField
val HTML_ATTRIBUTE_VALUES: PolySymbolKind = PolySymbolKind.Companion[NAMESPACE_HTML, "values"]

@JvmField
val HTML_SLOTS: PolySymbolKind = PolySymbolKind.Companion[NAMESPACE_HTML, "slots"]

/**
 * A special property to support symbols representing HTML attributes.
 **/
object HtmlAttributeValueProperty :
  PolySymbolProperty<PolySymbolHtmlAttributeValue>("html-attribute-value", PolySymbolHtmlAttributeValue::class.java)

fun PolySymbol.getHtmlAttributeValue(context: PsiElement?): PolySymbolHtmlAttributeValue? =
  if (this is PolySymbolMatchBase)
    this.reversedSegments().flatMap { it.symbols }.map { it.getHtmlAttributeValue(context) }.merge()
  else {
    val typeSupport = this[PolySymbolTypeSupport.TypeSupportProperty]
    if (typeSupport != null)
      typeSupport.withEvaluationLocation(context) {
        this[HtmlAttributeValueProperty]
      }
    else
      this[HtmlAttributeValueProperty]
  }