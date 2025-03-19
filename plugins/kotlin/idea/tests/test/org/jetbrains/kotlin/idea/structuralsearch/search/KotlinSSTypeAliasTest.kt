// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.structuralsearch.search

import org.jetbrains.kotlin.idea.structuralsearch.KotlinStructuralSearchTest

class KotlinSSTypeAliasTest : KotlinStructuralSearchTest() {
    fun testTypeAlias() { doTest("typealias '_ = Int", """
        package typeAlias
        
        <warning descr="SSR">typealias A = Int</warning>
        
        typealias B = String
    """.trimIndent()) }

    fun testAnnotated() { doTest("@Ann typealias '_ = '_", """
        @Target(AnnotationTarget.TYPEALIAS)
        annotation class Ann

        <warning descr="SSR">@Ann typealias aliasOne = List<String></warning>
        typealias aliasTwo = List<Int>
    """.trimIndent()) }
}