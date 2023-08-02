// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.highlighting

import org.jetbrains.kotlin.idea.highlighter.AbstractHighlightingOutsideSourceSetTest
import org.junit.internal.runners.JUnit38ClassRunner
import org.junit.runner.RunWith

@RunWith(JUnit38ClassRunner::class)
class K2HighlightingOutsideSourceSetTest : AbstractHighlightingOutsideSourceSetTest() {
    override fun isFirPlugin(): Boolean = true
}