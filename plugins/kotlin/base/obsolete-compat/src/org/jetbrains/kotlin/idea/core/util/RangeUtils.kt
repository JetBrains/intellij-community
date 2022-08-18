// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.core.util

import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement

@Deprecated("Use 'textRange' instead", ReplaceWith("textRange"))
val PsiElement.range: TextRange
    get() = textRange!!

@Deprecated("Use 'startOffset' instead", ReplaceWith("startOffset"))
val TextRange.start: Int
    get() = startOffset