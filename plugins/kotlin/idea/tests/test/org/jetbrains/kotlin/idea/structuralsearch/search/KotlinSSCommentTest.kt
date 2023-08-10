// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.structuralsearch.search

import org.jetbrains.kotlin.idea.structuralsearch.KotlinStructuralSearchTest

class KotlinSSCommentTest : KotlinStructuralSearchTest() {
    /**
     * EOL
     */
    fun testEol() { doTest("//", """
        <warning descr="SSR">//</warning>
        val foo1 = 1
        val foo2 = 1 <warning descr="SSR">//</warning>
        <warning descr="SSR">/**/</warning>
        val foo3 = 1
        val foo4 = 1 <warning descr="SSR">/**/</warning>
        val foo5 <warning descr="SSR">/**/</warning> = 1
        <warning descr="SSR">/**
         *
         */</warning>
        val foo6 = 1
        val foo7 = 1

        fun main() {
            <warning descr="SSR">//</warning>
            val bar1 = 1
            val bar2 = 1 <warning descr="SSR">//</warning>
            <warning descr="SSR">/**/</warning>
            val bar3 = 1
            val bar4 = 1 <warning descr="SSR">/**/</warning>
            val bar5 <warning descr="SSR">/**/</warning> = 1
            <warning descr="SSR">/**
             *
             */</warning>
            val bar6 = 1
            val bar7 = 1

            print(bar1 + bar2 + bar3 + bar4 + bar5 + bar6 + bar7)
        }
    """.trimIndent()) }

    fun testEolBeforeProperty() { doTest("""
        //
        val '_ = '_
    """.trimIndent(), """
        <warning descr="SSR">//
        val foo1 = 1</warning>
        <warning descr="SSR">val foo2 = 1 //</warning>
        <warning descr="SSR">/**/
        val foo3 = 1</warning>
        <warning descr="SSR">val foo4 = 1 /**/</warning>
        <warning descr="SSR">val foo5 /**/ = 1</warning>
        /**
         *
         */
        val foo6 = 1
        val foo7 = 1

        fun main() {
            <warning descr="SSR">//</warning>
            val bar1 = 1
            <warning descr="SSR">val bar2 = 1 //</warning>
            <warning descr="SSR">/**/</warning>
            val bar3 = 1
            <warning descr="SSR">val bar4 = 1 /**/</warning>
            <warning descr="SSR">val bar5 /**/ = 1</warning>
            /**
             *
             */
            val bar6 = 1
            val bar7 = 1

            print(bar1 + bar2 + bar3 + bar4 + bar5 + bar6 + bar7)
        }
    """.trimIndent()) }

    fun testEolBeforeClass() { doTest("""
        //
        class '_
    """.trimIndent(), """
        /**
         *
         */
        class Foo

        class Bar1

        <warning descr="SSR">//
        class Bar2</warning>

        <warning descr="SSR">/**/
        class Bar3</warning>

        class Bar4 {

            /**/
            fun f1(): Int = 1

            /**
             *
             */
            fun f2(): Int = 1

        }
    """.trimIndent()) }

    fun testEolInProperty() { doTest("val '_ = '_ //", """
        <warning descr="SSR">//
        val foo1 = 1</warning>
        <warning descr="SSR">val foo2 = 1 //</warning>
        <warning descr="SSR">/**/
        val foo3 = 1</warning>
        <warning descr="SSR">val foo4 = 1 /**/</warning>
        <warning descr="SSR">val foo5 /**/ = 1</warning>
        /**
         *
         */
        val foo6 = 1
        val foo7 = 1

        fun main() {
            //
            <warning descr="SSR">val bar1 = 1</warning>
            <warning descr="SSR">val bar2 = 1 //</warning>
            /**/
            <warning descr="SSR">val bar3 = 1</warning>
            <warning descr="SSR">val bar4 = 1 /**/</warning>
            <warning descr="SSR">val bar5 /**/ = 1</warning>
            /**
             *
             */
            val bar6 = 1
            val bar7 = 1

            print(bar1 + bar2 + bar3 + bar4 + bar5 + bar6 + bar7)
        }
    """.trimIndent()) }

    /**
     * Block
     */
    fun testBlock() { doTest("/**/", """
        <warning descr="SSR">//</warning>
        val foo1 = 1
        val foo2 = 1 <warning descr="SSR">//</warning>
        <warning descr="SSR">/**/</warning>
        val foo3 = 1
        val foo4 = 1 <warning descr="SSR">/**/</warning>
        val foo5 <warning descr="SSR">/**/</warning> = 1
        <warning descr="SSR">/**
         *
         */</warning>
        val foo6 = 1
        val foo7 = 1

        fun main() {
            <warning descr="SSR">//</warning>
            val bar1 = 1
            val bar2 = 1 <warning descr="SSR">//</warning>
            <warning descr="SSR">/**/</warning>
            val bar3 = 1
            val bar4 = 1 <warning descr="SSR">/**/</warning>
            val bar5 <warning descr="SSR">/**/</warning> = 1
            <warning descr="SSR">/**
             *
             */</warning>
            val bar6 = 1
            val bar7 = 1

            print(bar1 + bar2 + bar3 + bar4 + bar5 + bar6 + bar7)
        }
    """.trimIndent()) }

    fun testRegex() { doTest("// '_a:[regex( bar. )] = '_b:[regex( foo. )]", """
        val foo1 = 1
        val foo2 = 2

        fun main() {
            <warning descr="SSR">/* bar1 = foo1 */</warning>
            val bar1 = foo1
            // bar2 = 0
            val bar2 = 0
            <warning descr="SSR">// bar3 = foo2</warning>
            val bar3 = foo2
            // bar = foo1
            val bar = foo1

            print(bar1 + bar2 + bar3 + bar)
        }
    """.trimIndent()) }

    /**
     * KDoc
     */

    fun testKDoc() { doTest("""
        /**
         *
         */
    """.trimIndent(), """
        <warning descr="SSR">/**
         *
         */</warning>
        class Foo

        class Bar1

        //
        class Bar2

        /**/
        class Bar3

        class Bar4 {

            /**/
            fun f1(): Int = 1

            <warning descr="SSR">/**
             *
             */</warning>
            fun f2(): Int = 1

        }
    """.trimIndent()) }

    fun testKDocTag() { doTest("""
        /**
         * @'_ '_
         */
    """.trimIndent(), """
        <warning descr="SSR">/**
         * @param foo bar
         */</warning>
        class Foo1

        /**
         *
         */
        class Bar1

        <warning descr="SSR">/**
         * foo
         * @since 1
         */</warning>
        class Foo2

        <warning descr="SSR">/**
         * @property foo bar
         */</warning>
        class Bar2
    """.trimIndent()) }

    fun testKDocClass() { doTest("""
        /**
         *
         */
        class '_
    """.trimIndent(), """
        <warning descr="SSR">/**
         *
         */
        class Foo</warning>

        class Bar1

        //
        class Bar2

        /**/
        class Bar3

        class Bar4 {

            /**/
            fun f1(): Int = 1

            /**
             *
             */
            fun f2(): Int = 1

        }
    """.trimIndent()) }

    fun testKDocProperty() { doTest("""
        /**
         *
         */
        val '_ = '_
    """.trimIndent(), """
        //
        val foo1 = 1
        val foo2 = 1 //
        /**/
        val foo3 = 1
        val foo4 = 1 /**/
        val foo5 /**/ = 1
        <warning descr="SSR">/**
         *
         */
        val foo6 = 1</warning>
        val foo7 = 1

        fun main() {
            //
            val bar1 = 1
            val bar2 = 1 //
            /**/
            val bar3 = 1
            val bar4 = 1 /**/
            val bar5 /**/ = 1
            <warning descr="SSR">/**
             *
             */
            val bar6 = 1</warning>
            val bar7 = 1

            print(bar1 + bar2 + bar3 + bar4 + bar5 + bar6 + bar7)
        }
    """.trimIndent()) }
}