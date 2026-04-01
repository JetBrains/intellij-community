// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.fir.searcheverywhere

import org.jetbrains.kotlin.idea.base.plugin.KotlinPluginMode
import org.jetbrains.kotlin.idea.searcheverywhere.KotlinSearchEverywhereTestCase
import org.jetbrains.kotlin.idea.test.ExpectedPluginModeProvider
import org.jetbrains.kotlin.idea.test.setUpWithKotlinPlugin

class K2SuperSymbolTests : KotlinSearchEverywhereTestCase(), ExpectedPluginModeProvider {
    override val pluginMode: KotlinPluginMode = KotlinPluginMode.K2

    override fun setUp() {
        setUpWithKotlinPlugin { super.setUp() }
    }

    fun `test super class`(): Unit = doTest(
        text = """
            open class A
            class B<caret> : A()
        """.trimIndent(),
        action = "Super Class",
    )

    fun `test super interface`(): Unit = doTest(
        text = """
            interface A
            class B<caret> : A
        """.trimIndent(),
        action = "Super Class",
    )

    fun `test super several interfaces`(): Unit = doTest(
        text = """
            interface A
            interface C
            class B<caret> : A, C
        """.trimIndent(),
        action = "Super Class",
    )

    fun `test super class and interface `(): Unit = doTest(
        text = """
            interface A
            class C
            class B<caret> : A, C()
        """.trimIndent(),
        action = "Super Class",
    )

    fun `test super method`(): Unit = doTest(
        text = """
            abstract class A {
              abstract fun foo()
            }
            class B : A() {
              override fun f<caret>oo() = Unit
            }
        """.trimIndent(),
        action = "Super Method",
    )

    fun `test super property`(): Unit = doTest(
        text = """
            abstract class A {
              abstract val a: Int
            }
            class B : A() {
              override val a<caret>: Int = 5
            }
        """.trimIndent(),
        action = "Super Property",
    )

    fun `test super property from getter`(): Unit = doTest(
        text = """
            abstract class A {
              abstract val a: Int
            }
            class B : A() {
              override val a: Int 
                g<caret>et() = 5
            }
        """.trimIndent(),
        action = "Super Property",
    )

    private fun doTest(text: String, action: String): Unit = configureAndCheckActions(
        text = text,
        expected = listOf(action),
        pattern = "go to super",
    )
}