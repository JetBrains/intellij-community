// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlin.idea.codeInsight.hints

import com.intellij.codeInsight.hints.LinearOrderInlayRenderer
import org.jetbrains.kotlin.idea.test.KotlinLightCodeInsightFixtureTestCase

/**
 * [org.jetbrains.kotlin.idea.codeInsight.hints.KotlinCallChainHintsProvider]
 */
class KotlinCallChainHintsProviderTest : KotlinLightCodeInsightFixtureTestCase() {
    val chainLib = """
        class Foo {
            var foo = Foo()
            var bar = Bar()
            fun foo(block: () -> Unit = {}) = Foo()
            fun nullFoo(): Foo? = null
            fun bar(block: () -> Unit = {}) = Bar()
            fun nullBar(): Bar? = null
            operator fun inc() = Foo()
            operator fun get(index: Int) = Bar()
            operator fun invoke() = Bar()
        }

        class Bar {
            var foo = Foo()
            var bar = Bar()
            fun foo(block: () -> Unit = {}) = Foo()
            fun nullFoo(): Foo? = null
            fun bar(block: () -> Unit = {}) = Bar()
            fun nullBar(): Bar? = null
            operator fun inc() = Bar()
            operator fun get(index: Int) = Foo()
            operator fun invoke() = Foo()
        }
    """.trimIndent()

    fun `test simple`() = doTest("""
        fun main() {
            Foo().bar()<hint text="[temp:///src/foo.kt:311]Bar"/>
                .foo()<hint text="[temp:///src/foo.kt:0]Foo"/>
                .bar()<hint text="[temp:///src/foo.kt:311]Bar"/>
                .foo()
        }
    """.trimIndent())

    fun `test multiline calls`() = doTest("""
        fun main() {
            Foo()
                .bar {

                }<hint text="[temp:///src/foo.kt:311]Bar"/>
                .foo()<hint text="[temp:///src/foo.kt:0]Foo"/>
                .bar {}<hint text="[temp:///src/foo.kt:311]Bar"/>
                .foo()
        }
    """.trimIndent())

    fun `test duplicated builder`() = doTest("""
        fun main() {
            Foo().foo()<hint text="[temp:///src/foo.kt:0]Foo"/>
                .bar().foo()
                .bar()<hint text="[temp:///src/foo.kt:311]Bar"/>
                .foo()
        }
    """.trimIndent())

    fun `test comments`() = doTest("""
         fun main() {
            Foo().bar() // comment
                .foo()<hint text="[temp:///src/foo.kt:0]Foo"/>
                .bar()<hint text="[temp:///src/foo.kt:311]Bar"/>
                .foo()// comment
                .bar()
                .bar()
        }
    """.trimIndent())

    fun `test properties`() = doTest("""
        fun main() {
            Foo().bar<hint text="[temp:///src/foo.kt:311]Bar"/>
                .foo<hint text="[temp:///src/foo.kt:0]Foo"/>
                .bar.bar<hint text="[temp:///src/foo.kt:311]Bar"/>
                .foo()<hint text="[temp:///src/foo.kt:0]Foo"/>
                .bar<hint text="[temp:///src/foo.kt:311]Bar"/>
                .bar()
        }
    """.trimIndent())

    fun `test postfix operators`() = doTest("""
        fun main() {
            Foo().bar()!!<hint text="[temp:///src/foo.kt:311]Bar"/>
                .foo++<hint text="[temp:///src/foo.kt:0]Foo"/>
                .foo[1]<hint text="[temp:///src/foo.kt:311]Bar"/>
                .nullFoo()!!<hint text="[temp:///src/foo.kt:0]Foo"/>
                .foo()()<hint text="[temp:///src/foo.kt:311]Bar"/>
                .bar
        }
    """.trimIndent())

    fun `test safe dereference`() = doTest("""
        fun main() {
            Foo()
                .nullBar()<hint text="[[temp:///src/foo.kt:311]Bar ?]"/>
                ?.foo!!<hint text="[temp:///src/foo.kt:0]Foo"/>
                .bar()
        }
    """.trimIndent())

    fun `test several call chains`() = doTest("""
        fun main() {
            Foo()
                .nullBar()<hint text="[[temp:///src/foo.kt:311]Bar ?]"/>
                ?.foo!!<hint text="[temp:///src/foo.kt:0]Foo"/>
                .bar()

            Bar().foo()<hint text="[temp:///src/foo.kt:0]Foo"/>
                .bar().foo()
                .bar()<hint text="[temp:///src/foo.kt:311]Bar"/>
                .foo()
        }
    """.trimIndent())

    fun `test nested call chains`() = doTest("""
        fun main() {
            Foo().foo {
                    Bar().foo()<hint text="[temp:///src/foo.kt:0]Foo"/>
                         .bar()<hint text="[temp:///src/foo.kt:311]Bar"/>
                         .foo()
                }<hint text="[temp:///src/foo.kt:0]Foo"/>
                .bar()<hint text="[temp:///src/foo.kt:311]Bar"/>
                .foo()<hint text="[temp:///src/foo.kt:0]Foo"/>
                .bar()
        }
    """.trimIndent())

    fun `test nullable and notnullable types rotation of the same type`() = doTest("""
        fun main() {
            Foo().nullBar()<hint text="[[temp:///src/foo.kt:311]Bar ?]"/>
                ?.bar!!<hint text="[temp:///src/foo.kt:311]Bar"/>
                .nullBar()<hint text="[[temp:///src/foo.kt:311]Bar ?]"/>
                ?.foo()
        }
    """.trimIndent())

    private fun doTest(text: String) {
        myFixture.configureByText("foo.kt", "$chainLib\n$text")
        myFixture.testInlays({ (it.renderer as LinearOrderInlayRenderer<*>).toString() }, { it.renderer is LinearOrderInlayRenderer<*> })
    }
}