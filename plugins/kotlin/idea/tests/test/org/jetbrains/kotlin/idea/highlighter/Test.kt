// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.highlighter

@Target(AnnotationTarget.CLASS, AnnotationTarget.EXPRESSION)
@Retention(AnnotationRetention.SOURCE)
annotation class Ann

//@Ann class A
//
//fun bar(block: () -> Int) = block()
//
//private
//fun foo() {
//    1 + @Ann 2
//
//    @Ann 3 + 4
//
//    bar @Ann { 1 }
//
//    @Err
//    5
//}
//
//@Err class Err1
//
//class NotAnn
//@NotAnn
//class C
