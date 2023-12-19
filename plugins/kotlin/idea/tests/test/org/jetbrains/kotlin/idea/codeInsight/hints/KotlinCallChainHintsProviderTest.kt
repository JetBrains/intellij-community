// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.codeInsight.hints

import com.intellij.testFramework.utils.inlays.declarative.DeclarativeInlayHintsProviderTestCase
import org.intellij.lang.annotations.Language

/**
 * [org.jetbrains.kotlin.idea.codeInsight.hints.KotlinCallChainHintsProvider]
 */
class KotlinCallChainHintsProviderTest : DeclarativeInlayHintsProviderTestCase() {
    @Language("kotlin")
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

    fun `test error type`() = doTest("""
        // NO_HINTS
        interface Baz {
            fun foo(): Foo = Foo()
        }

        class Outer : Baz {
            class Nested {
                val x = this@Outer.foo()
                    .bar()
                    .foo()
                    .bar()
            }
        }
    """.trimIndent())

    fun `test simple`() = doTest("""
        fun main() {
            Foo().bar()/*<# [Bar:kotlin.fqn.class]Bar #>*/
                .foo()/*<# [Foo:kotlin.fqn.class]Foo #>*/
                .bar()/*<# [Bar:kotlin.fqn.class]Bar #>*/
                .foo()
        }
    """.trimIndent())

    fun `test multiline calls`() = doTest("""
        fun main() {
            Foo()
                .bar {

                }/*<# [Bar:kotlin.fqn.class]Bar #>*/
                .foo()/*<# [Foo:kotlin.fqn.class]Foo #>*/
                .bar {}/*<# [Bar:kotlin.fqn.class]Bar #>*/
                .foo()
        }
    """.trimIndent())

    fun `test duplicated builder`() = doTest("""
        fun main() {
            Foo().foo()/*<# [Foo:kotlin.fqn.class]Foo #>*/
                .bar().foo()
                .bar()/*<# [Bar:kotlin.fqn.class]Bar #>*/
                .foo()
        }
    """.trimIndent())

    fun `test comments`() = doTest("""
        fun main() {
            Foo().bar() // comment
                .foo()/*<# [Foo:kotlin.fqn.class]Foo #>*/
                .bar()/*<# [Bar:kotlin.fqn.class]Bar #>*/
                .foo()// comment
                .bar()
                .bar()
        }
    """.trimIndent())

    fun `test properties`() = doTest("""
        fun main() {
            Foo().bar/*<# [Bar:kotlin.fqn.class]Bar #>*/
                .foo/*<# [Foo:kotlin.fqn.class]Foo #>*/
                .bar.bar/*<# [Bar:kotlin.fqn.class]Bar #>*/
                .foo()/*<# [Foo:kotlin.fqn.class]Foo #>*/
                .bar
        }
    """.trimIndent())

    fun `test postfix operators`() = doTest("""
        fun main() {
            Foo().bar()!!/*<# [Bar:kotlin.fqn.class]Bar #>*/
                .foo++/*<# [Foo:kotlin.fqn.class]Foo #>*/
                .foo[1]/*<# [Bar:kotlin.fqn.class]Bar #>*/
                .nullFoo()!!/*<# [Foo:kotlin.fqn.class]Foo #>*/
                .foo()()/*<# [Bar:kotlin.fqn.class]Bar #>*/
                .bar
        }
    """.trimIndent())

    fun `test safe dereference`() = doTest("""
        fun main() {
            Foo()
                .nullBar()/*<# [Bar:kotlin.fqn.class]Bar|? #>*/
                ?.foo!!/*<# [Foo:kotlin.fqn.class]Foo #>*/
                .bar()
        }
    """.trimIndent())

    fun `test several call chains`() = doTest("""
            fun main() {
                Foo()
                    .nullBar()/*<# [Bar:kotlin.fqn.class]Bar|? #>*/
                    ?.foo!!/*<# [Foo:kotlin.fqn.class]Foo #>*/
                    .bar()
            
                Bar().foo()/*<# [Foo:kotlin.fqn.class]Foo #>*/
                    .bar().foo()
                    .bar()/*<# [Bar:kotlin.fqn.class]Bar #>*/
                    .foo()
            }
        """.trimIndent())

    fun `test nested call chains`() = doTest("""
        fun main() {
            Foo().foo {
                    Bar().foo()/*<# [Foo:kotlin.fqn.class]Foo #>*/
                         .bar()/*<# [Bar:kotlin.fqn.class]Bar #>*/
                         .foo()
                }/*<# [Foo:kotlin.fqn.class]Foo #>*/
                .bar()/*<# [Bar:kotlin.fqn.class]Bar #>*/
                .foo()/*<# [Foo:kotlin.fqn.class]Foo #>*/
                .bar()
        }
    """.trimIndent())

    fun `test nullable and notnullable types rotation of the same type`() = doTest("""
        fun main() {
            Foo().nullBar()/*<# [Bar:kotlin.fqn.class]Bar|? #>*/
                ?.bar!!/*<# [Bar:kotlin.fqn.class]Bar #>*/
                .nullBar()/*<# [Bar:kotlin.fqn.class]Bar|? #>*/
                ?.foo()
        }
    """.trimIndent())

    private fun doTest(@Language("kotlin") text: String) {
        doTestProvider("foo.kt", "$chainLib\n$text", org.jetbrains.kotlin.idea.codeInsight.hints.declarative.KotlinCallChainHintsProvider())
    }
}