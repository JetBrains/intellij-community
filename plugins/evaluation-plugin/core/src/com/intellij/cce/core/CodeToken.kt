// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.cce.core

class CodeToken(override val text: String,
                override val offset: Int,
                val properties: TokenProperties = TokenProperties.UNKNOWN
) : CodeElement
