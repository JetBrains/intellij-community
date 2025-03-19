// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.webSymbols.html.impl

import com.intellij.webSymbols.html.WebSymbolHtmlAttributeValue
import java.util.Objects

internal class WebSymbolHtmlAttributeValueImpl(override val kind: WebSymbolHtmlAttributeValue.Kind? = null,
                                               override val type: WebSymbolHtmlAttributeValue.Type? = null,
                                               override val required: Boolean? = null,
                                               override val default: String? = null,
                                               override val langType: Any? = null) : WebSymbolHtmlAttributeValue {

  override fun equals(other: Any?): Boolean =
    other === this
    || other is WebSymbolHtmlAttributeValue
    && other.kind == kind
    && other.type == type
    && other.required == required
    && other.default == default
    && other.langType == langType

  override fun hashCode(): Int =
    Objects.hash(kind, type, required, default, langType)

}