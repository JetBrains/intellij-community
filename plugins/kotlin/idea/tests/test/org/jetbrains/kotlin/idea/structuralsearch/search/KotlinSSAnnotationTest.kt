/*
 * Copyright 2000-2020 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.idea.structuralsearch.search

import org.jetbrains.kotlin.idea.structuralsearch.KotlinSSResourceInspectionTest

class KotlinSSAnnotationTest : KotlinSSResourceInspectionTest() {
    override fun getBasePath(): String = "annotation"

    fun testAnnotation() { doTest("@Foo") }

    fun testAnnotationArrayParameter() { doTest("@Foo(['_*])") }

    fun testClassAnnotation() { doTest("@A class '_") }

    fun testClass2Annotations() { doTest("@'_Annotation{2,100} class '_Name") }

    fun testFunAnnotation() { doTest("@A fun '_() { println(0) }") }

    fun testClassAnnotationArgs() { doTest("@A(0) class '_()") }

    fun testUseSiteTarget() { doTest("class '_(@get:'_ val '_ : '_)") }

    fun testAnnotatedExpression() { doTest("@'_{0,1} { println() }") }

    fun testAnnotatedExpressionZero() { doTest("fun '_() = @'_{0,0} { println() }") }
}