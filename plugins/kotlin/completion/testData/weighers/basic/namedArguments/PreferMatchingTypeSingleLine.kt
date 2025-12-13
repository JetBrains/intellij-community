// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package test

class Foo(
    otherArgA: Int,
    otherArgB: Int,
    somePrefixA: Int,
    somePrefixB: Int,
)

fun foo(somePrefixValue: Int, somePrefixUnrelated: String) {
    Foo(somePrefix<caret>)
}

// ORDER: somePrefixValue
// ORDER: somePrefixA =
// ORDER: somePrefixB =
// ORDER: somePrefixUnrelated