// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.coverage

import com.intellij.coverage.view.AbstractPsiConditionsCoverageTest
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@RunWith(JUnit4::class)
class KotlinConditionsCoverageTest : AbstractPsiConditionsCoverageTest() {
    @Test
    fun `test kotlin switches hints`() = assertHints("KtSwitches", true)

    @Test
    fun `test kotlin conditions hints`() = assertHints("KtConditions", true)

    @Test
    fun `test kotlin comments and parentheses`() = assertHints("KtCommentsAndParentheses", true)

    @Test
    fun `test kotlin all conditions`() = assertHints("KtAllConditions", true)

    @Test
    fun `test kotlin nullability`() = assertHints("KtNullability", true)

    @Test
    fun `test kotlin jacoco switches hints`() = assertHints("KtSwitches", false)

    @Test
    fun `test kotlin jacoco conditions hints`() = assertHints("KtConditions", false)

    @Test
    fun `test kotlin jacoco comments and parentheses`() = assertHints("KtCommentsAndParentheses", false)

    @Test
    fun `test kotlin jacoco all conditions`() = assertHints("KtAllConditions", false)

    @Test
    fun `test kotlin jacoco nullability`() = assertHints("KtNullability", false)
}
