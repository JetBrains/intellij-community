// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.util

class <caret>A {
}

class Foo {
    fun f(a: A, count:Int) {

    }
}
class Bar {
    fun f(a:A, count:Int) {

    }
}

fun Bar.f(a:A) {
    this.f(a, 0)
}

fun f(a:A, count: Int) {

}

fun f(a:A) {

}

fun f (b:Bar):List<A> {

}
fun f (b:Bar):List<A > { //spaces in type reference

}
