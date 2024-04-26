// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.k2.codeinsight.structuralsearch.search.filters

import org.jetbrains.kotlin.idea.k2.codeinsight.structuralsearch.KotlinStructuralSearchProfile
import org.jetbrains.kotlin.idea.k2.codeinsight.structuralsearch.KotlinStructuralSearchTest

class KotlinSSTypeModifierTest : KotlinStructuralSearchTest() {
    fun testShortNameTypeFilter() { doTest("val '_x:[exprtype(Int)]", """
        <warning descr="SSR">val a = 1</warning>
        <warning descr="SSR">val b: Int = 2</warning>
        val c = 1.0
        val d: Double = 1.0
    """.trimIndent()) }

    fun testFqNameTypeFilter() { doTest("val '_x:[exprtype(A.B.Foo)]", """
        class Foo
        <warning descr="SSR">val foo1 = A.B.Foo()</warning>
        val bar1 = Foo()

        class A {
            class Foo
            <warning descr="SSR">val foo2 = B.Foo()</warning>
            val bar2 = Foo()
            class B {
                class Foo
                <warning descr="SSR">val foo2 = Foo()</warning>
                val bar3 = A.Foo()
                class C {
                        class Foo
                        val bar4 = Foo()
                    }
            }
        }
    """.trimIndent()) }

    fun testWithinHierarchyTypeFilter() { doTest("val '_x:[exprtype(*Number)]", """
        <warning descr="SSR">val a = 1</warning>
        <warning descr="SSR">val b: Int = 2</warning>
        <warning descr="SSR">val c = 1.0</warning>
        <warning descr="SSR">val d: Double = 1.0</warning>
        <warning descr="SSR">val e: Number = 1</warning>
        <warning descr="SSR">val f: Number = 1.0</warning>
        val g = "Hello World"
    """.trimIndent()) }

    fun testNullableType() { doTest("'_('_:[exprtype(Int?)])", """
        var nullable: Int? = null
        var notNullable: Int = 0

        fun f(s: Int?): Int? { return s }

        fun main() {
            <warning descr="SSR">f(nullable)</warning>
            f(notNullable)
        }
    """.trimIndent()) }

    fun testNullableTypeHierarchy() { doTest("val '_:[exprtype(*A)]", """
        open class A
        class B : A()
        class C : A()

        <warning descr="SSR">val b: B = B()</warning>
        val c: C? = null
    """.trimIndent()) }
    
    fun testNullableFunctionType() { doTest("'_('_:[exprtype(\\(\\(\\) -> Unit\\)?)])", """
        fun testFun(body: (() -> Unit)?) {
            <warning descr="SSR">testFun2(body)</warning>
        }

        fun testFun2(body: (() -> Unit)?) {
            body?.invoke()
        }
    """.trimIndent()) }
    
    fun testNull() { doTest("'_('_:[exprtype(null)])", """
        fun foo(x: Int?): Int? { return x }

        fun main() {
            foo(1)
            <warning descr="SSR">foo(null)</warning>
        }
    """.trimIndent()) }

    fun testArgs() { doTest("val '_:[exprtype(Array<Int>)]", """
        <warning descr="SSR">val foo = Array(1, { 1 })</warning>
        val bar = Array(1, { "1" })
    """.trimIndent()) }

    fun testFunctionType() { doTest("val '_:[exprtype( (String) -> Int )]", """
        <warning descr="SSR">val foo = { x: String -> x.hashCode() }</warning>
        val bar1 = { x: Int -> x.hashCode() }
        val bar2 = { x: Int -> "${"$"}x" }
    """.trimIndent()) }

    fun testFunctionType2() { doTest("val '_:[exprtype( (String, Int) -> Boolean )]", """
        <warning descr="SSR">val foo = { x: String, y: Int -> "${"$"}y" == x }</warning>
        val bar1 = { x: Int, y: String -> "${"$"}x" == y }
        val bar2 = { x: String, y: Int -> "${"$"}x ${"$"}y" }
    """.trimIndent()) }

    fun testFunctionType3() { doTest("val '_:[exprtype( () -> Unit )]", """
        <warning descr="SSR">val foo = { Unit }</warning>
        <warning descr="SSR">val anonFunc = fun() {}</warning>
        val bar1 = { 1 }
        val bar2 = { x: Int -> Unit }
    """.trimIndent()) }

    fun testInVariance() { doTest("fun '_('_:[exprtype(Array<in String>)])", """
        <warning descr="SSR">fun foo(foo: Array<in String>) { print(foo) }</warning>
        fun bar1(foo: Array<String>) { print(foo) }
        fun bar2(foo: Array<out String>) { print(foo) }
    """.trimIndent()) }

    fun testOutVariance() { doTest("fun '_('_:[exprtype(Array<out Any>)])", """
        <warning descr="SSR">fun foo(foo: Array<out Any>) { print(foo) }</warning>
        fun bar1(foo: Array<Any>) { print(foo) }
        fun bar2(foo: Array<in Any>) { print(foo) }
    """.trimIndent()) }

    fun testFunctionTypeReceiver() { doTest("val '_ = '_:[exprtype(TestClass.\\(\\) -> Unit)]", """
        class TestClass {
            fun testClassFun() {}
        }
        <warning descr="SSR">val prop: TestClass.() -> Unit = {this.testClassFun()}</warning>
        val bar: () -> Unit = {}
    """.trimIndent()) }
    
    fun testSuspendFunctionType() { doTest("val '_ = '_:[exprtype(suspend \\(\\) -> Unit)]", """
        <warning descr="SSR">val propSuspendedFun: suspend () -> Unit = {}</warning>
    """.trimIndent()) }

    fun testFunctionTypeSupertype() { doTest("val '_:[exprtype(*\\(\\) -> Unit)]", """
        fun myTopFun() { }
        fun main() {
            <warning descr="SSR">val v: () -> Unit = ::myTopFun</warning>
            v()
        }
        class Inheritor : () -> Unit {
            override fun invoke() {}
            <warning descr="SSR">val i = Inheritor()</warning>
        }
    """.trimIndent()) }

    fun testStarProjection() { doTest("fun '_('_ : Foo<*>)", """
        class Foo<T>
        <warning descr="SSR">fun foo1(x: Foo<*>) { print(x) }</warning>
        fun bar(x: Foo<Int>) { print(x) }
    """.trimIndent()) }
    
    // Elements where type filter is enabled

    fun testTypeValueArgument() { doTest("'_('_:[exprtype(String)])", """
        fun print(x: Any) { x.hashCode() }

        fun main() {
            <warning descr="SSR">print("1")</warning>
            print(1)
        }
    """.trimIndent()) }

    fun testTypeBinaryExpression() { doTest("'_:[exprtype(Int)] + '_:[exprtype(Float)]", """
        fun main() {
            print(<warning descr="SSR">1 + 1f</warning>)
            print(2 + 2)
            print(3f + 3f)
        }
    """.trimIndent()) }

    fun testTypeBinaryExpressionWithTypeRHS() { doTest("'_:[exprtype(Any)] as '_", """
        fun foo(x: Int): Any = when(x) {
            0 -> 1
            else -> "1"
        }

        val foo1 = <warning descr="SSR">foo(0) as Int</warning>
    """.trimIndent()) }

    fun testTypeIsExpression() { doTest("'_:[exprtype(Any)] is '_", """
        fun foo(x: Int): Any = when(x) {
            0 -> 1
            else -> "1"
        }

        val foo1 = <warning descr="SSR">foo(0) is Int</warning>
    """.trimIndent()) }

    fun testTypeBlockExpression() { doTest("{'_ -> '_:[exprtype(Int)]}", """
        val x = <warning descr="SSR">{ f: Int -> f }</warning>
        val y = { f: String -> f }
    """.trimIndent()) }

    fun testTypeArrayAccessArrayExpression() { doTest("'_:[exprtype(Array<Int>)]['_]", """
        val x = Array( 1, { 1 })
        val y = "hello"

        fun foo() {
            print(<warning descr="SSR">x[0]</warning>)
            print(y[0])
        }
    """.trimIndent()) }

    // KT-64724
    fun _testTypeArrayAccessIndicesNode() { doTest("'_['_:[exprtype(String)]]", """
        val x = mapOf(1 to 1)
        val y = mapOf("a" to 1)

        fun foo() {
            print(x[1])
            print(<warning descr="SSR">y["a"]</warning>)
        }
    """.trimIndent()) }

    fun testTypePostfixExpression() { doTest("'_:[exprtype(Int)]++", """
        var x = 1
        var y = 1.0

        fun main() {
            <warning descr="SSR">x++</warning>
            y++
            print(x + y)
        }
    """.trimIndent()) }

    fun testTypeDotQualifiedExpressionClassReference() { doTest("'_:[exprtype(String)].'_", """
        val x = "1"
        val y = 1

        val String.foo: String
        get() = "foo"

        fun main() {
            print(<warning descr="SSR">x.foo</warning>)
            print(<warning descr="SSR">x.hashCode()</warning>)
            print(y.hashCode())
        }
    """.trimIndent()) }

    fun testTypeDotQualifiedExpressionEnumReference() { doTest("'_:[exprtype(com.jetbrains.foo.Bar)].'_", """
        package com.jetbrains.foo

        enum class Bar { FOO }
        
        enum class Foo { BAR }

        fun main() {
            print(<warning descr="SSR">Bar.FOO</warning>)
            //print(Foo.BAR)
        }
    """.trimIndent()) }

    fun testTypeSafeQualifiedExpression() { doTest("'_:[exprtype(A?)]?.'_", """
        class A { val x = 1 }
        val x : A? = null
        val y = A()
        val foo = <warning descr="SSR">x?.x</warning>
        val bar = y.x
    """.trimIndent()) }

    fun testTypeCallExpression() { doTest("'_:[exprtype(A.Companion)].'_", """
        class A {
            companion object {
                fun foo() { }
            }
        }

        fun main() {
            <warning descr="SSR">A.foo()</warning>
        }
    """.trimIndent()) }

    fun testTypeCallExpressionFromJava() {
        myFixture.addFileToProject("pkg/A.java", """
            package pkg;
            
            public class A {
                public static void foo() { }
            }
        """.trimIndent())
        doTest("'_:[exprtype(pkg.A)].'_", """
            import pkg.A
            
            fun main() {
                <warning descr="SSR">A.foo()</warning>
            }
        """.trimIndent())
    }

    fun testTypeCallExpressionWithoutReceiver() {
        myFixture.addFileToProject("A.kt", """
            object A {
                fun foo() { }
            }
        """.trimIndent())
        myFixture.addFileToProject("B.kt", """
            object B {
                fun foo() { }
            }
        """.trimIndent())
        doTest("'_{0,1}:[exprtype(A)].'_()", """
            import B.foo
            
            fun main() {
                <warning descr="SSR">A.foo()</warning>
                B.foo()
                foo()
            }
    """.trimIndent())
    }

    fun testTypeCallExpressionWithoutReceiverImported() {
        myFixture.addFileToProject("A.kt", """
            object A {
                fun foo() { }
            }
        """.trimIndent())
        myFixture.addFileToProject("B.kt", """
            object B {
                fun foo() { }
            }
        """.trimIndent())
        doTest("'_{0,1}:[exprtype(A)].'_()", """
            import A.foo
            
            fun main() {
                <warning descr="SSR">A.foo()</warning>
                B.foo()
                <warning descr="SSR">foo()</warning>
            }
    """.trimIndent())
    }

    fun testTypeCallExpressionWithoutReceiverImportedJava() {
        myFixture.addFileToProject("A.java", """
            class A {
                public static void foo() { }
            }
        """.trimIndent())
        myFixture.addFileToProject("B.kt", """
            object B {
                fun foo() { }
            }
        """.trimIndent())
        doTest("'_{0,1}:[exprtype(A)].'_()", """
            import A.foo
            
            fun main() {
                <warning descr="SSR">A.foo()</warning>
                B.foo()
                <warning descr="SSR">foo()</warning>
            }
    """.trimIndent())
    }


    fun testTypeCallExpressionWithoutReceiverOnHigherOrderFunction() {
        myFixture.addFileToProject("A.kt", """
            object A {
                fun foo() { }
            }
        """.trimIndent())
        doTest("'_{0,1}:[exprtype(A)].foo()", """
            fun bar1(foo: () -> Unit) {
                <warning descr="SSR">A.foo()</warning>
                foo()
            }       
    """.trimIndent())
    }

    fun testTypeCallExpressionWithoutReceiverOnHigherOrderExtensionFunction() {
        myFixture.addFileToProject("A.kt", """
            object A { }
        """.trimIndent())
        doTest("'_{0,1}:[exprtype(A)].foo()", """
            fun bar1(foo: A.() -> Unit) {
                <warning descr="SSR">A.foo()</warning>
            }
    """.trimIndent())
    }

    fun testTypeCallExpressionWithoutReceiverJava() {
        myFixture.addFileToProject("A.java", """
            class A {
                public static void foo() { }
            }
        """.trimIndent())
        myFixture.addFileToProject("B.java", """
            class B {
                public static void foo() { }
            }
        """.trimIndent())
        doTest("'_{0,1}:[exprtype(A)].'_()", """
            import B.foo
            
            fun main() {
                <warning descr="SSR">A.foo()</warning>
                B.foo()
                foo()
            }
    """.trimIndent())
    }

    fun `test call with qualified receiver from Kotlin companion`() {
        myFixture.addFileToProject("FooBar.kt", """
            package foo.bar
            
            class FooBar {
                companion object {
                    fun foo() { } 
                }
            }
        """.trimIndent())
        doTest("'_:[exprtype(foo.bar.FooBar.Companion)].foo()", """
            fun main() {
                <warning descr="SSR">foo.bar.FooBar.foo()</warning>
            }
        """.trimIndent())
    }

    // TODO fix this test, type resolve on qualified resolvers to Java code doesn't work
    fun `_test call with qualified receiver from Java static`() {
        myFixture.addFileToProject("foo/bar/FooBar.java", """
            package foo.bar;
            
            public class FooBar {
                public static void foo() { }
            }
        """.trimIndent())
        doTest("'_:[exprtype(foo.bar.FooBar)].foo()", """
            fun main() {
                <warning descr="SSR">foo.bar.FooBar.foo()</warning>
            }
        """.trimIndent())
    }

    fun testTypeCallableReferenceExpression() { doTest("'_:[exprtype(A)]::'_", """
        class A {
            val x = 1
            fun foo() { print(<warning descr="SSR">this::x</warning>) }
        }

        class B {
            val x = 1
            fun foo() { print(this::x) }
        }
    """.trimIndent()) }

    fun testTypeSimpleNameStringTemplateEntry() { doTest(""" "$$'_:[exprtype(Int)]" """, """
        val x = 1
        val y = "1"

        val foo = <warning descr="SSR">"${"$"}x"</warning>
        val bar = "${"$"}y"
    """.trimIndent()) }

    fun testTypeBlockStringTemplateEntry() { doTest(""" "${'$'}{ '_:[exprtype(Int)] }" """, """
        val x = 1
        val foo = <warning descr="SSR">"${"$"}{x.hashCode()}"</warning>
        val bar = "${'$'}"
    """.trimIndent()) }

    fun testTypePropertyAccessor() { doTest("val '_ get() = '_:[exprtype(Int)]", """
        <warning descr="SSR">val x: Int
            get() = 1</warning>

        val y: String
            get() = "1"
    """.trimIndent(), KotlinStructuralSearchProfile.PROPERTY_CONTEXT) }

    fun testTypeWhenEntry() { doTest("when { '_ -> '_:[exprtype(Int)] }", """
        val x = 1

        val y = <warning descr="SSR">when(x) {
            2 -> 2
            else -> 0
        }</warning>

        val z = when(x) {
            2 -> "2"
            else -> ""
        }
    """.trimIndent()) }
}