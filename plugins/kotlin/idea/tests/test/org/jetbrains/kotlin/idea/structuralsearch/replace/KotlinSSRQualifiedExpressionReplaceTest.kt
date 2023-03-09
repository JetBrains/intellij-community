// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.structuralsearch.replace

import org.jetbrains.kotlin.idea.structuralsearch.KotlinStructuralReplaceTest
import org.jetbrains.kotlin.idea.structuralsearch.filters.MatchCallSemanticsModifier
import org.jetbrains.kotlin.idea.structuralsearch.filters.OneStateFilter

class KotlinSSRQualifiedExpressionReplaceTest : KotlinStructuralReplaceTest() {
    fun testQualifiedExpressionReceiverWithCountFilter() {
        doTest(
            searchPattern = "'_BEFORE{0,1}.'_FUN()",
            replacePattern = "'_BEFORE.foo('_ARG)",
            match = """
                class X {
                  fun foo() { }
                  fun bar() { }
                }
                
                fun main() {
                    bar()
                }
            """.trimIndent(),
            result = """
                class X {
                  fun foo() { }
                  fun bar() { }
                }
                
                fun main() {
                    foo()
                }
            """.trimIndent()
        )
    }

    fun testDoubleQualifiedExpression() {
        doTest(
            searchPattern = """
                '_REC.foo = '_INIT
                '_REC.bar = '_INIT
            """.trimIndent(),
            replacePattern = """
                '_REC.fooBar = '_INIT
            """.trimIndent(),
            match = """
                class X {
                  fun foo() { }
                  fun bar() { }
                  fun fooBar() { }
                }
                
                fun main() {
                    val x = X()
                    x.foo = true
                    x.bar = true
                }
            """.trimIndent(),
            result = """
                class X {
                  fun foo() { }
                  fun bar() { }
                  fun fooBar() { }
                }
                
                fun main() {
                    val x = X()
                    x.fooBar = true
                }
            """.trimIndent()
        )
    }

    fun testMakeArgumentReceiver() {
        doTest(
            searchPattern = "'_REC{0,1}.foo('_ARG1, '_ARG2)",
            replacePattern = "'_ARG1.foo('_ARG2)",
            match = """
                class X {
                  fun foo(param1: String, param2: String) { }
                  
                  companion object {
                    const val STR = ""
                  }
                }
                
                fun String.bar(param2: String) { println(this) }
                    
                fun main() {
                    val x = X()
                    x.foo(X.STR, "")
                }
            """.trimIndent(),
            result = """
                class X {
                  fun foo(param1: String, param2: String) { }
                  
                  companion object {
                    const val STR = ""
                  }
                }
                
                fun String.bar(param2: String) { println(this) }
                    
                fun main() {
                    val x = X()
                    X.STR.foo("")
                }
            """.trimIndent()
        )
    }

    fun testMakeArgumentReceiverWithLambda() {
        doTest(
            searchPattern = "'_REC{0,1}.'_:[_${MatchCallSemanticsModifier.CONSTRAINT_NAME}(${OneStateFilter.ENABLED})]('_ARG1, '_ARG2)",
            replacePattern = "'_ARG1.foo('_ARG2)",
            match = """
                class X {
                  fun foo(param1: String, param2: (String) -> Unit) { }
                  
                  companion object {
                    const val STR = ""
                  }
                }
                
                fun String.bar(param2: (String) -> Unit) { println(this) }
                    
                fun main() {
                    val x = X()
                    x.foo(X.STR) { str -> println() }
                }
            """.trimIndent(),
            result = """
                class X {
                  fun foo(param1: String, param2: (String) -> Unit) { }
                  
                  companion object {
                    const val STR = ""
                  }
                }
                
                fun String.bar(param2: (String) -> Unit) { println(this) }
                    
                fun main() {
                    val x = X()
                    X.STR.foo({ str -> println() })
                }
            """.trimIndent()
        )
    }

    fun testMakeArgumentReceiverWithLambdaJava() {
        myFixture.addFileToProject("X.java", """
            class X {
                public static String foo(String str, java.util.function.Predicate<Boolean> condition) { return ""; }
            }
        """.trimIndent())
        myFixture.addFileToProject("Y.java", """
            class Y {
                public String getFoo() { return ""; }
            }
        """.trimIndent())
        doTest(
            searchPattern = "'_REC{0,1}.'_:[_${MatchCallSemanticsModifier.CONSTRAINT_NAME}(${OneStateFilter.ENABLED})]('_ARG1, '_ARG2)",
            replacePattern = "'_ARG1.bar('_ARG2)",
            match = """
                fun String.bar(param2: (Boolean) -> Unit) { println(this) }
                    
                fun main() {
                    val y = Y()
                    X.foo(y.foo) { boolean -> println() }
                }
            """.trimIndent(),
            result = """
                fun String.bar(param2: (Boolean) -> Unit) { println(this) }
                    
                fun main() {
                    val y = Y()
                    y.foo.bar({ boolean -> println() })
                }
            """.trimIndent()
        )
    }
}