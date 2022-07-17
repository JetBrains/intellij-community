package com.intellij.cce.core

class CodeToken(val text: String,
                val offset: Int,
                val length: Int,
                val properties: TokenProperties = TokenProperties.UNKNOWN
)