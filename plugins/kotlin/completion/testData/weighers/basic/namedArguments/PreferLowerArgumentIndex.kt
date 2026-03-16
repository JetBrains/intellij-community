// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package test

class Foo(
    somePrefixB: Int,
    somePrefixC: Int,
    somePrefixA: Int,
)

fun foo() {
    Foo(
        somePrefix<caret>
    )
}

// ORDER: somePrefixB =
// ORDER: somePrefixC =
// ORDER: somePrefixA =
// IGNORE_K1