// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.k2.codeinsight.structuralsearch.search

import org.jetbrains.kotlin.idea.k2.codeinsight.structuralsearch.KotlinStructuralSearchProfile
import org.jetbrains.kotlin.idea.k2.codeinsight.structuralsearch.KotlinStructuralSearchTest
import org.jetbrains.kotlin.idea.k2.codeinsight.structuralsearch.filters.AlsoMatchValModifier
import org.jetbrains.kotlin.idea.k2.codeinsight.structuralsearch.filters.AlsoMatchVarModifier
import org.jetbrains.kotlin.idea.k2.codeinsight.structuralsearch.filters.OneStateFilter

class KotlinSSPropertyTest : KotlinStructuralSearchTest() {
    fun testVar() { doTest(pattern = "var '_", highlighting = """
        fun main() {
            val foo = 1
            <warning descr="SSR">var bar = 1</warning>
            print(foo + bar)
        }
    """.trimIndent()) }

    fun testVal() { doTest(pattern = "val '_", highlighting = """
        fun main() {
            <warning descr="SSR">val foo = 1</warning>
            var bar: Int = 1
            print(foo + bar)
        }
    """.trimIndent()) }

    fun testValType() { doTest(pattern = "val '_ : Int", highlighting = """
        class A {
            val foo1 = Int()
            <warning descr="SSR">val foo2 : A.Int = Int()</warning>
            class Int
        }
        fun main(): String {
            val bar = "1"
            <warning descr="SSR">val bar1: Int = 1</warning>
            <warning descr="SSR">val bar2: kotlin.Int = 1</warning>
            return "${"$"}bar + ${"$"}bar1 + ${"$"}bar2"
        }
    """.trimIndent()) }

    fun testValFqType() { doTest(pattern = "val '_ : Foo.Int", highlighting = """
        class Foo {
            val foo = Int()
            <warning descr="SSR">val bar : Foo.Int</warning>
            init {bar = Int()}
            class Int
        }
        fun main() {
            val foo2: Int = 1
            print(foo2)
        }
    """.trimIndent()) }

    // KT-64724
    fun _testValComplexFqType() { doTest(pattern = "val '_ : '_<'_<'_, (Foo.Int) -> Int>>", highlighting = """
        class Foo { class Int }
        <warning descr="SSR">val foo1: List<Pair<String, (Foo.Int) -> kotlin.Int>> = listOf()</warning>
        val foo2 = listOf("foo" to { _: Foo.Int -> 2 })
        val bar1: List<Pair<String, (Int) -> Int>> = listOf()
        val bar2 = listOf("bar" to { _: Int -> 2 })
    """.trimIndent()) }

    fun testValInitializer() { doTest(pattern = "val '_ = 1", highlighting = """
        fun main() {
            <warning descr="SSR">val foo = 1</warning>
            val foo2: Int
            var bar: Int = 1
            foo2 = 1
            <warning descr="SSR">val bar2: Int = 1</warning>
            <warning descr="SSR">val bar3: Int = (1)</warning>
            <warning descr="SSR">val bar4: Int = (((1)))</warning>
            print(foo + foo2 + bar + bar2 + bar3 + bar4)
        }
    """.trimIndent()) }

    fun testValReceiverType() { doTest(pattern = "val '_ : ('_T) -> '_U = '_", highlighting = """
        <warning descr="SSR">val foo: (Int) -> String? = { "${"$"}it" }</warning>
        val bar: String = "bar"
        val bar2: (Int, Int) -> String? = TODO()
    """.trimIndent()) }

    fun testVarTypeProjection() { doTest(pattern = "var '_ : Comparable<'_T>", highlighting = """
        <warning descr="SSR">var foo: Comparable<Int> = TODO()</warning>
        var bar: List<Int> = TODO()
    """.trimIndent()) }

    fun testVarStringAssign() { doTest(pattern = "var '_  = \"Hello world\"", highlighting = """
        <warning descr="SSR">var a = "Hello world"</warning>
        <warning descr="SSR">var b = ("Hello world")</warning>
        <warning descr="SSR">var c = (("Hello world"))</warning>
    """.trimIndent()) }

    fun testVarStringAssignPar() { doTest(pattern = "var '_  = (\"Hello world\")", highlighting = """
        var a = "Hello world"
        <warning descr="SSR">var b = ("Hello world")</warning>
        var c = (("Hello world"))
    """.trimIndent()) }

    fun testVarRefAssign() { doTest(pattern = "var '_  = a", highlighting = """
        val a = "Hello World"
        <warning descr="SSR">var b = a</warning>
        <warning descr="SSR">var c = (a)</warning>
        <warning descr="SSR">var d = ((a))</warning>
    """.trimIndent()) }

    fun testVarNoInitializer() { doTest(pattern = "var '_ = '_{0,0}", highlighting = """
        class MyClass()
        abstract class MyAbstractClass {
            var a = MyClass()
            <warning descr="SSR">abstract var b: MyClass</warning>
            <warning descr="SSR">lateinit var c: MyAbstractClass</warning>
        }
    """.trimIndent()) }

    fun testVarGetterModifier() {
        doTest(pattern = """
            var '_Field = '_ 
                @'_Ann get() = '_
        """.trimIndent(), highlighting = """
            annotation class MyAnn
            <warning descr="SSR">var tlProp: Int = 1
                @MyAnn get() { return field * 3 }</warning>
            class A {
                <warning descr="SSR">var myProp: Int = 1
                    @MyAnn get() = field * 3</warning>
                var myPropTwo = 2
                    get() = field * 2
            }
        """.trimIndent(), KotlinStructuralSearchProfile.PROPERTY_CONTEXT) }

    fun testVarSetterModifier() {
        doTest(pattern = """
            var '_Field = '_ 
                private set('_x) { '_* }
        """.trimIndent(), highlighting = """
            class A {
                <warning descr="SSR">var myProp: Int = 1
                    private set(value) {
                        field = value * 3
                    }</warning>
                var myPropTwo = 2
                    set(value) {
                        field = value * 3
                    }
            }
        """.trimIndent(), KotlinStructuralSearchProfile.PROPERTY_CONTEXT) }

    fun testFunctionType() { doTest(pattern = "val '_ : ('_{2,2}) -> Unit", highlighting = """
        class MyClass {
            <warning descr="SSR">val fooThree: (Int, String) -> Unit = { i: Int, s: String -> }</warning>
            val fooTwo: (String) -> Unit = {}
            val fooOne: () -> Unit = {}
        }
    """.trimIndent()) }

    fun testFunctionTypeNamedParameter() { doTest(pattern = "val '_ : ('_) -> '_", highlighting = """
        class MyClass {
            <warning descr="SSR">val funTwo: (s: String) -> Unit = {}</warning>
            <warning descr="SSR">val funOne: (String) -> Unit = {}</warning>
        }
    """.trimIndent()) }

    fun testReturnTypeReference() { doTest(pattern = "val '_ : ('_) -> Unit", highlighting = """
        class MyClass {
            val funOne: (String) -> String = { it }
            <warning descr="SSR">val funTwo: (String) -> Unit = { print(it) }</warning>
        }
    """.trimIndent()) }

    fun testNullableFunctionType() { doTest(pattern = "val '_ : '_ ?", highlighting = """
        class ClassOne {
            <warning descr="SSR">val valOne: (() -> Unit)? = {}</warning>
            <warning descr="SSR">val valTwo: ClassOne? = null</warning>
        }
    """.trimIndent()) }

    fun testReceiverTypeReference() { doTest(pattern = "val Int.'_ : '_", highlighting = """
        val String.foo: Int get() = 1
        <warning descr="SSR">val Int.foo: Int get() = 1</warning>
    """.trimIndent()) }

    fun testReceiverFqTypeReference() { doTest(pattern = "val kotlin.Int.'_ : '_", highlighting = """
        val String.foo: Int get() = 1
        val Int.foo: Int get() = 1
        <warning descr="SSR">val kotlin.Int.bar: Int get() = 1</warning>
        class A {
            class Int
            val Int.foo: kotlin.Int get() = 1
        }
    """.trimIndent()) }

    fun testAlsoMatchValModifier() { doTest(pattern = "var '_:[_${AlsoMatchValModifier.CONSTRAINT_NAME}(${OneStateFilter.ENABLED})]", """
        fun main() {
            <warning descr="SSR">var x = 1</warning>
            <warning descr="SSR">val y = 1</warning>
            print(x + y)
        }
    """.trimIndent()) }

    fun testAlsoMatchVarModifier() { doTest(pattern = "val '_:[_${AlsoMatchVarModifier.CONSTRAINT_NAME}(${OneStateFilter.ENABLED})]", """
        fun main() {
            <warning descr="SSR">var x = 1</warning>
            <warning descr="SSR">val y = 1</warning>
            print(x + y)
        }
    """.trimIndent()) }
}