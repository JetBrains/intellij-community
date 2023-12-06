// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.structuralsearch.search

import org.jetbrains.kotlin.idea.structuralsearch.KotlinStructuralSearchTest

class KotlinSSTargetedTest : KotlinStructuralSearchTest() {
    fun testTargetAnyField() {
        doTest(
            """
                class '_Class {  
                    val 'Field+ = '_Init?
                }
            """.trimIndent(), """
            public class BaseClass : SuperClass() {
                val <warning descr="SSR">a</warning>: Int = 3

                val <warning descr="SSR">b</warning> = 45

                val <warning descr="SSR">y</warning>: Any = 34

                val <warning descr="SSR">str</warning>: String = "test"
            }

            open class SuperClass : SuperSuperClass() {
                val <warning descr="SSR">c</warning> = 30
            }

            abstract class SuperSuperClass {
                val <warning descr="SSR">d</warning> = 50
            }
        """.trimIndent()
        )
    }

    fun testTargetSpecificField() {
        doTest(
            """
                class '_Class {  
                    val 'Field+ = 45
                }
            """.trimIndent(), """
            public class BaseClass : SuperClass() {
                val a: Int = 3

                val <warning descr="SSR">b</warning>: Int = 45

                val y: Any = 34

                val str: String = "test"
            }

            open class SuperClass : SuperSuperClass() {
                val c = 30
            }

            abstract class SuperSuperClass {
                val <warning descr="SSR">d</warning> = 45
            }
        """.trimIndent()
        )
    }
}