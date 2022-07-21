// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.searcheverywhere

class SuperSymbolTests : KotlinSearchEverywhereTestCase() {
    fun `test super class`(): Unit = doTest(
        text = """
                open class A
                class B<caret> : A()
            """.trimIndent(),
        action = "Go to Super Class or Interface",
    )

    fun `test super function`(): Unit = doTest(
        text = """
            abstract class A {
              abstract fun foo()
            }
            class B : A() {
              override fun f<caret>oo() = Unit
            }
        """.trimIndent(),
        action = "Go to Super Method",
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
        action = "Go to Super Property",
    )

    private fun doTest(text: String, action: String): Unit = configureAndCheckActions(
        text = text,
        expected = listOf(action),
        pattern = "go to super",
    )
}