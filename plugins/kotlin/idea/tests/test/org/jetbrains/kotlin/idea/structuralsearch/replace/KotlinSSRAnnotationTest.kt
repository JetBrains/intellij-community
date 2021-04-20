/*
 * Copyright 2000-2020 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.idea.structuralsearch.replace

import org.jetbrains.kotlin.idea.structuralsearch.KotlinSSRReplaceTest

class KotlinSSRAnnotationTest : KotlinSSRReplaceTest() {
    fun testClassReplacement() {
        doTest(
            searchPattern = "class '_ID",
            replacePattern = "class '_ID",
            match = """
                annotation class Foo
                
                @Foo
                class Bar
            """.trimIndent(),
            result = """
                annotation class Foo
                
                @Foo
                class Bar
            """.trimIndent()
        )
    }

    fun testAnnotatedClassReplacement() {
        doTest(
            searchPattern = "@Foo class '_ID",
            replacePattern = "@Foo class '_ID",
            match = """
                annotation class Foo
                
                @Foo
                class Bar
            """.trimIndent(),
            result = """
                annotation class Foo
                
                @Foo
                class Bar
            """.trimIndent()
        )
    }
}