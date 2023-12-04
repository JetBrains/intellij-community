// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.highlighting

import org.jetbrains.kotlin.idea.highlighter.AbstractCustomHighlightUsageHandlerTest

abstract class AbstractK2HighlightUsagesTest: AbstractCustomHighlightUsageHandlerTest() {
    override fun isFirPlugin(): Boolean = true
}