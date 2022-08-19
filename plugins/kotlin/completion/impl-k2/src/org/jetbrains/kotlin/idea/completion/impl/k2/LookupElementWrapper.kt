// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.completion

import com.intellij.codeInsight.lookup.LookupElement

internal fun interface LookupElementWrapper {
    fun wrap(element: LookupElement): LookupElement
}


internal fun List<LookupElementWrapper>.wrap(element: LookupElement): LookupElement =
    fold(element) { currentElement, wrapper -> wrapper.wrap(currentElement) }
