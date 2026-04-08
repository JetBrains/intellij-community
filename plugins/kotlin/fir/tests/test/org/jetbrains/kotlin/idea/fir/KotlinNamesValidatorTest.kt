// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.fir

import com.intellij.lang.refactoring.NamesValidator
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase
import org.jetbrains.kotlin.idea.base.plugin.KotlinPluginMode
import org.jetbrains.kotlin.idea.refactoring.KotlinNamesValidator
import org.jetbrains.kotlin.idea.test.ExpectedPluginModeProvider
import org.jetbrains.kotlin.idea.test.setUpWithKotlinPlugin
import org.junit.Assert
import org.junit.internal.runners.JUnit38ClassRunner
import org.junit.runner.RunWith

@RunWith(JUnit38ClassRunner::class)
class KotlinNamesValidatorTest : LightJavaCodeInsightFixtureTestCase(), ExpectedPluginModeProvider {
    override val pluginMode: KotlinPluginMode = KotlinPluginMode.K2

    override fun setUp() {
        setUpWithKotlinPlugin { super.setUp() }
    }

    val validator: NamesValidator = KotlinNamesValidator()

    private fun isKeyword(string: String) = validator.isKeyword(string, null)
    private fun isIdentifier(string: String) = validator.isIdentifier(string, null)

    fun testKeywords() {
        Assert.assertTrue(isKeyword("val"))
        Assert.assertTrue(isKeyword("class"))
        Assert.assertTrue(isKeyword("fun"))

        Assert.assertFalse(isKeyword("constructor"))
        Assert.assertFalse(isKeyword("123"))
        Assert.assertFalse(isKeyword("a.c"))
        Assert.assertFalse(isKeyword("-"))
    }

    fun testIdentifiers() {
        Assert.assertTrue(isIdentifier("abc"))
        Assert.assertTrue(isIdentifier("q_q"))
        Assert.assertTrue(isIdentifier("constructor"))
        Assert.assertTrue(isIdentifier("`val`"))

        Assert.assertFalse(isIdentifier("val"))
        Assert.assertFalse(isIdentifier("class"))
        Assert.assertFalse(isIdentifier("fun"))
        Assert.assertFalse(isIdentifier("1"))
        Assert.assertFalse(isIdentifier("1abc"))
        Assert.assertFalse(isIdentifier("a.c"))
        Assert.assertFalse(isIdentifier("-"))
        Assert.assertFalse(isIdentifier("``"))
    }
}
