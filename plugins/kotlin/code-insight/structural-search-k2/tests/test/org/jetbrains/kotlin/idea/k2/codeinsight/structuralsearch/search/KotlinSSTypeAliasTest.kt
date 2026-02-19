// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.k2.codeinsight.structuralsearch.search

import org.jetbrains.kotlin.idea.k2.codeinsight.structuralsearch.KotlinStructuralSearchTest

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