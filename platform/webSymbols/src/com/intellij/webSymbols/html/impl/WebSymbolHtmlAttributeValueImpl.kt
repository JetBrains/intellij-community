// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.webSymbols.html.impl

import com.intellij.webSymbols.html.WebSymbolHtmlAttributeValue

internal data class WebSymbolHtmlAttributeValueImpl(override val kind: WebSymbolHtmlAttributeValue.Kind? = null,
                                                    override val type: WebSymbolHtmlAttributeValue.Type? = null,
                                                    override val required: Boolean? = null,
                                                    override val default: String? = null,
                                                    override val langType: Any? = null) : WebSymbolHtmlAttributeValue