package com.intellij.javascript.web.symbols

data class WebSymbolHtmlAttributeValueData(override val kind: WebSymbol.AttributeValueKind? = null,
                                           override val type: WebSymbol.AttributeValueType? = null,
                                           override val required: Boolean? = null,
                                           override val default: String? = null,
                                           // TODO rename to symbolType
                                           override val jsType: Any? = null) : WebSymbol.AttributeValue