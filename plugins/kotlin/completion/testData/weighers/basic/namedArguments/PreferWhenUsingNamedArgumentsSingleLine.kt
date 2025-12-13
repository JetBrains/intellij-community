// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package test

class Foo(
    otherArgA: Int,
    otherArgB: Int,
    somePrefixA: Int,
    somePrefixB: Int,
)

fun foo(somePrefixValue: Int, somePrefixUnrelated: String) {
    Foo(1, otherArgB = 5, somePrefix<caret>)
}

// ORDER: somePrefixA =
// ORDER: somePrefixB =
// ORDER: somePrefixValue
// ORDER: somePrefixUnrelated
// IGNORE_K1