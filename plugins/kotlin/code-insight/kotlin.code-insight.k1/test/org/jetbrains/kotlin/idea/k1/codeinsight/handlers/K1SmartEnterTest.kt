// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k1.codeinsight.handlers

import org.jetbrains.kotlin.idea.completion.test.AbstractSmartEnterTest

class K1SmartEnterTest: AbstractSmartEnterTest() {
    fun testClassBodyHasInitializedSuperType() = doFileTest(
        """
            open class A
            class B : A()<caret>
        """,
        """
            open class A
            class B : A() {
                <caret>
            }
        """
    )

    fun testObjectExpressionBody1() = doFileTest(
        """
            interface I
            val a = object : I<caret>
            """,
        """
            interface I
            val a = object : I {
                <caret>
            }
            """
    )

    fun testObjectExpressionBody2() = doFileTest(
        """
            interface I
            val a = object : I<caret>

            val b = ""
            """,
        """
            interface I
            val a = object : I {
                <caret>
            }

            val b = ""
            """
    )

    fun testClassBodyHasNotInitializedJavaInterfaceSuperType() = doFileTest(
        before = """
                    class A : I<caret>
                """,
        after = """
                    class A : I {
                        <caret>
                    }
                """,
        javaFile = """
                    interface I {}
                """
    )

    fun testClassBody1() = doFileTest(
        """
            class Foo<caret>
            """,
        """
            class Foo {
                <caret>
            }
            """
    )

    fun testClassBody2() = doFileTest(
        """
            class <caret>Foo
            """,
        """
            class Foo {
                <caret>
            }
            """
    )

    fun testExtensionLambdaParam() = doFileTest(
        """
            fun foo(a: Any, block: Any.() -> Unit) {
            }
            fun test() {
                foo(Any()<caret>)
            }
            """,
        """
            fun foo(a: Any, block: Any.() -> Unit) {
            }
            fun test() {
                foo(Any()) { <caret>}
            }
            """
    )

    fun testClassBodyHasNotInitializedAbstractJavaClassSuperType() = doFileTest(
        before = """
                    class A : C<caret>
                """,
        after = """
                    class A : C() {
                        <caret>
                    }
                """,
        javaFile = """
                    abstract class C {}
                """
    )

    fun testClassBodyHasNotInitializedSuperType() = doFileTest(
        """
            open class A
            class B : A<caret>
        """,
        """
            open class A
            class B : A() {
                <caret>
            }
        """
    )

    fun testClassBodyHasNotInitializedJavaInterfaceAndClassSuperType() = doFileTest(
        before = """
                    class A : C, I<caret>
                """,
        after = """
                    class A : C(), I {
                        <caret>
                    }
                """,
        javaFile = """
                    interface I {}
                    class C {}
                """
    )

    fun testClassBodyHasNotInitializedJavaClassSuperType() = doFileTest(
        before = """
                    class A : C<caret>
                """,
        after = """
                    class A : C() {
                        <caret>
                    }
                """,
        javaFile = """
                    class C {}
                """
    )

    fun testClassBodyHasNotInitializedSuperType2() = doFileTest(
        """
            sealed class A(val s: String)
            class B : A<caret>
        """,
        """
            sealed class A(val s: String)
            class B : A() {
                <caret>
            }
        """
    )

    fun testClassBodyHasNotInitializedSuperType3() = doFileTest(
        """
            interface I
            interface J
            abstract class A
            class B : I, A, J<caret>
        """,
        """
            interface I
            interface J
            abstract class A
            class B : I, A(), J {
                <caret>
            }
        """
    )

    fun testLambdaParam() = doFileTest(
        """
            fun foo(a: Any, block: () -> Unit) {
            }
            fun test() {
                foo(Any()<caret>)
            }
            """,
        """
            fun foo(a: Any, block: () -> Unit) {
            }
            fun test() {
                foo(Any()) { <caret>}
            }
            """
    )
}