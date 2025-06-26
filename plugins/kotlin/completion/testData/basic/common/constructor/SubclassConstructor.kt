// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
interface Foo {

}

class FooImpl(a: Int) : Foo {}

fun test(): Foo = <caret>

// IGNORE_K1
// EXIST: {"lookupString": "FooImpl", "tailText": "(a: Int) (<root>)" }
// ABSENT: {"lookupString": "Foo", "tailText": "() (<root>)" }