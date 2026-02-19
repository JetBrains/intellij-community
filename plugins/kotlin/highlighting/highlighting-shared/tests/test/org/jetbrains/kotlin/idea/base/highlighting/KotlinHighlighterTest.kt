// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.base.highlighting

import com.intellij.testFramework.LightPlatformTestCase
import org.jetbrains.kotlin.idea.highlighter.KotlinHighlighter
import org.jetbrains.kotlin.idea.highlighter.KotlinHighlightingColors
import org.jetbrains.kotlin.lexer.KtTokens
import org.junit.internal.runners.JUnit38ClassRunner
import org.junit.runner.RunWith

@RunWith(JUnit38ClassRunner::class)
class KotlinHighlighterTest : LightPlatformTestCase() {
    
    fun testAmpersandHighlighting() {
        val highlighter = KotlinHighlighter()
        val attributes = highlighter.getTokenHighlights(KtTokens.AND)
        
        // Verify that AND token maps to AMPERSAND color
        assertTrue(attributes.isNotEmpty())
        assertEquals(KotlinHighlightingColors.AMPERSAND, attributes[0])
    }
}