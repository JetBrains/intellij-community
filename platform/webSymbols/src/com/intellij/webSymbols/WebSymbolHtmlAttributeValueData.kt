// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.webSymbols

data class WebSymbolHtmlAttributeValueData(override val kind: WebSymbol.AttributeValueKind? = null,
                                           override val type: WebSymbol.AttributeValueType? = null,
                                           override val required: Boolean? = null,
                                           override val default: String? = null,
                                           override val langType: Any? = null) : WebSymbol.AttributeValue