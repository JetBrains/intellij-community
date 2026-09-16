// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.polySymbols.html.impl

import com.intellij.polySymbols.html.PolySymbolHtmlAttributeValue

internal class PolySymbolHtmlAttributeValueImpl(override val kind: PolySymbolHtmlAttributeValue.Kind? = null,
                                                override val type: PolySymbolHtmlAttributeValue.Type? = null,
                                                override val required: Boolean? = null,
                                                override val default: String? = null,
                                                override val langType: Any? = null) : PolySymbolHtmlAttributeValue {

  override fun equals(other: Any?): Boolean =
    other === this
    || other is PolySymbolHtmlAttributeValue
    && other.kind == kind
    && other.type == type
    && other.required == required
    && other.default == default
    && other.langType == langType

  override fun hashCode(): Int {
    var result = kind.hashCode()
    result = 31 * result + type.hashCode()
    result = 31 * result + required.hashCode()
    result = 31 * result + default.hashCode()
    result = 31 * result + langType.hashCode()
    return result
  }

}