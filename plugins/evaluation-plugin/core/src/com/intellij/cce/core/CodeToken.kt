package com.intellij.cce.core

class CodeToken(override val text: String,
                override val offset: Int,
                val properties: TokenProperties = TokenProperties.UNKNOWN
) : CodeElement
