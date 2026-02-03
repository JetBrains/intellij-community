// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.k2.codeinsight.structuralsearch.search

import org.jetbrains.kotlin.idea.k2.codeinsight.structuralsearch.KotlinStructuralSearchTest

class KotlinSSClassTest : KotlinStructuralSearchTest() {
    fun testClass() { doTest("class A", """
        <warning descr="SSR">class A</warning>
        class B
    """.trimIndent()) }

    fun testClassDoubleInheritance() { doTest("class '_ : A, B()", """
        interface A
        abstract class B
        <warning descr="SSR">class C : A, B()</warning>
        <warning descr="SSR">class D : B(), A</warning>
        class E : A
        class F : B()
        class G
    """.trimIndent()) }

    fun testClassSingleInheritance() { doTest("class '_ : A", """
        interface A
        abstract class B
        <warning descr="SSR">class C : A, B()</warning>
        <warning descr="SSR">class D : B(), A</warning>
        <warning descr="SSR">class E : A</warning>
        class F : B()
        class G
    """.trimIndent()) }

    fun testClassExtendsParam() { doTest("class '_ : '_('_, '_)", """
        open class A(val b: Int, val c: String)
        <warning descr="SSR">class B(b: Int, c: String) : A(b, c)</warning>
    """.trimIndent()) }

    fun testClassDelegation() { doTest("class '_(b: B) : A by b", """
        interface A { fun x(): String }
        class B(val c: Int) : A { override fun x(): String { return "${"$"}c" } }
        class C(val d: Int) : A { override fun x(): String { return "${"$"}d" } }
        <warning descr="SSR">class D(b: B) : A by b</warning>
        class E(b: C) : A by b
        class F(val b: B) : A { override fun x(): String { return "${"$"}b" } }
        interface G { fun x(): String }
        class H(val c: Int) : G { override fun x(): String { return "${"$"}c" } }
        class I(b: H) : G by b
    """.trimIndent()) }

    fun testClassConstrPrim() { doTest("class A(b: Int, c: String)", """
        <warning descr="SSR">class A(val b: Int, val c: String)</warning>
    """.trimIndent()) }

    fun testClassConstrPrimModifier() { doTest("class '_ private constructor()", """
        <warning descr="SSR">class A private constructor()</warning>
        class B()
    """.trimIndent()) }

    fun testClassConstrPrimDiffType() { doTest("class A(b: Int, c: String)", """
        class A(val b: String, val c: String)
    """.trimIndent()) }

    fun testClassConstrPrimDefaultValue() { doTest("class '_(b: Int, c: String = \"a\")", """
        <warning descr="SSR">class A(val b: Int, val c: String = "a")</warning>
        class B(val b: Int, val c: String = "b")
        class C(val b: Int, val c: String)
    """.trimIndent()) }

    fun testClassConstrSec() { doTest("""
        class '_(val b: Int) {
            var c: String? = null

            constructor(b: Int, c: String) : this(b) {
                this.c = c
            }
        }
    """.trimIndent(), """
        <warning descr="SSR">class A(val b: Int) {
            var c: String? = null
        
            constructor(b: Int, c: String) : this(b) {
                this.c = c
            }
        }</warning>
        
        class B(val b: Int) {
            var c: String? = null
        
            constructor(b: Int, c: String) : this(b) {
                println()
                this.c = c
            }
        }
        """.trimIndent())
    }

    fun testClassConstrSecModifier() { doTest("""
        class '_(val b: Int) {
            var c: String? = null

            private constructor(b: Int, c: String) : this(b) {
                this.c = c
            }
        }
    """.trimIndent(), """
        <warning descr="SSR">class A(val b: Int) {
            var c: String? = null

            private constructor(b: Int, c: String) : this(b) {
                this.c = c
            }
        }</warning>
        
        class B(val b: Int) {
            var c: String? = null
        
            constructor(b: Int, c: String) : this(b) {
                this.c = c
            }
        }    
    """.trimIndent())
    }

    fun testClassTypeParam() { doTest("class '_<T, R>(val a: T, val b: R, val c: T)", """
        <warning descr="SSR">class A<T, R>(val a: T, val b: R, val c: T)</warning>
        class B<R, T>(val a: T, val b: R, val c: T)
        class C<T, R>(val a: T, val b: R, val c: R)
        class D<T>(val a: T, val b: T, val c: T)
        class E(val a: Int, val b: Int, val c: Int)
    """.trimIndent()) }

    fun testClassTypeParamExtBound() { doTest("class '_<'_, '_ : List<*>>(val a: T, val b: R, val c: T)", """
        <warning descr="SSR">class A<T, R : List<*>>(val a: T, val b: R, val c: T)</warning>
        class B<T, R>(val a: T, val b: R, val c: T)
    """.trimIndent()) }

    fun testClassTypeParamProjection() { doTest("class '_<'_T : Comparable<'_T>>", """
        <warning descr="SSR">class Foo<T : Comparable<T>></warning>
    """.trimIndent()) }

    fun testClassTypeParamVariance() { doTest("class '_<out V>", """
        <warning descr="SSR">class A<out V></warning>
        class B<in V>
        class C<V>
    """.trimIndent()) }

    fun testInterface() { doTest("interface '_", """
        <warning descr="SSR">interface A</warning>
        class B
    """.trimIndent()) }

    fun testDataClass() { doTest("data class '_", """
        <warning descr="SSR">data class A(val c: Int)</warning>
        class B(val c: Int)
    """.trimIndent()) }

    fun testEnumClass() { doTest("enum class '_", """
        <warning descr="SSR">enum class A</warning>
        class B
    """.trimIndent()) }

    fun testInnerClass() { doTest("inner class '_", """
        class A {
            <warning descr="SSR">inner class B</warning>
            class C
        }
    """.trimIndent()) }

    fun testSealedClass() { doTest("sealed class '_", """
        <warning descr="SSR">sealed class A</warning>
        class B
    """.trimIndent()) }

    fun testClassAbstractModifier() { doTest("abstract class '_", """
        <warning descr="SSR">abstract class A</warning>
    """.trimIndent()) }

    fun testClassOpenModifier() { doTest("open class '_", """
        <warning descr="SSR">open class A</warning>
        class B
    """.trimIndent()) }

    fun testClassPublicModifier() { doTest("public class '_", """
        <warning descr="SSR">public class A</warning>
        class B
    """.trimIndent()) }

    fun testClassInternalModifier() { doTest("internal class '_", """
        <warning descr="SSR">internal class A</warning>
        class B
    """.trimIndent()) }

    fun testClassPrivateModifier() { doTest("private class '_", """
        <warning descr="SSR">private class A</warning>
        class B
    """.trimIndent()) }

    fun testClassInit() { doTest("""
        class '_ {
            init {
                val a = 3
                println(a)
            }
        }
    """.trimIndent(), """
       <warning descr="SSR">class A {
           init {
               val a = 3
               println(a)
           }
       }</warning>

       class B {
           init {
               val b = 3
               println(b)
           }
       }
    """.trimIndent())
    }

    fun testClassProperty() { doTest("""
        class '_ {
            lateinit var 'a
        }
    """.trimIndent(), """
        import java.util.*

        class Foo {
            var bar1 = 1
            lateinit var <warning descr="SSR">foo2</warning>: Random
        }

        class Bar {
            var bar1 = 1
            var bar2 = Random(1)
        }
    """.trimIndent())
    }

    fun testClassVarIdentifier() { doTest("class '_:[regex( Foo.* )]", """
        class Fo0
        <warning descr="SSR">class Foo</warning>
        <warning descr="SSR">class Foo1</warning>
    """.trimIndent()) }

    fun testTwoClasses() { doTest("""
        class '_a:[regex( Foo(1)* )]
        class '_b:[regex( Bar(1)* )]
    """.trimIndent(), """
        <warning descr="SSR">class Foo1</warning>
        class Bar1
        class Foo11
        class Bar2
    """.trimIndent())
    }

    fun testClassOptionalVars() { doTest("""
        class '_Class {  
            var '_Field* = '_Init?
        }
    """.trimIndent(), """
        <warning descr="SSR">class Empty</warning>
        <warning descr="SSR">class BoxOfVal { val v1 = 0 }</warning>
        <warning descr="SSR">class BoxOfVar { var v1 = 0 }</warning>
        <warning descr="SSR">class BoxOfFun { fun f1() { } }</warning>        
    """.trimIndent())
    }

    fun testClassOptionalParam() { doTest("class '_Class ('_Param* : '_Type)", """
        <warning descr="SSR">class MyClass</warning>
        <warning descr="SSR">class MyClassTwo()</warning>
        <warning descr="SSR">class MyClassThree constructor()</warning>
    """.trimIndent()) }

    fun testClassValParameter() { doTest("class '_(val '_ : '_)", """
        class MyClass(a: String) { init { print(a) } }
        <warning descr="SSR">class MyClassTwo(val a: String)</warning>
        class MyClassThree(var a: String)
    """.trimIndent()) }
}