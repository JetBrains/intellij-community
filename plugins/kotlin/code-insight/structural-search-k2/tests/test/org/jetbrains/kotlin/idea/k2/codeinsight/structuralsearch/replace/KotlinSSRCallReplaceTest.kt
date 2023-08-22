// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.structuralsearch.replace

import org.jetbrains.kotlin.idea.k2.codeinsight.structuralsearch.KotlinStructuralReplaceTest

class KotlinSSRCallReplaceTest : KotlinStructuralReplaceTest() {
    fun `test replace simple call name`() {
        doTest(
            searchPattern = "foo()",
            replacePattern = "bar()",
            match = """
                fun foo() { }
                
                fun bar() { }
                
                fun main() {
                    foo()
                }
            """.trimIndent(),
            result = """
                fun foo() { }
                
                fun bar() { }
                
                fun main() {
                    bar()
                }
            """.trimIndent()
        )
    }

    fun `test remove all params from a call`() {
        doTest(
            searchPattern = "foo('_PARAMS*)",
            replacePattern = "foo()",
            match = """
                fun foo(x: Int, y: Int) { }
                
                fun foo() { }
                
                fun main() {
                    foo(0, 0)
                }
            """.trimIndent(),
            result = """
                fun foo(x: Int, y: Int) { }
                
                fun foo() { }
                
                fun main() {
                    foo()
                }
            """.trimIndent()
        )
    }

    fun `test remove single argument from a call`() {
        doTest(
            searchPattern = "foo('_BEFORE*, 2, '_AFTER*)",
            replacePattern = "foo('_BEFORE, '_AFTER)",
            match = """
                fun foo(x: Int, y: Int, z: Int) { }
                
                fun foo(x: Int, z: Int) { }
                
                fun main() {
                    foo(1, 2, 3)
                }
            """.trimIndent(),
            result = """
                fun foo(x: Int, y: Int, z: Int) { }
                
                fun foo(x: Int, z: Int) { }
                
                fun main() {
                    foo(1, 3)
                }
            """.trimIndent()
        )
    }
}