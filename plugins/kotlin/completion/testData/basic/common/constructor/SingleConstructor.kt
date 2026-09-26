// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
class SomeClassWithConstructor(a: Int) {
}

fun create(): SomeClassWithConstructor {
    return SomeClassWithConstructor<caret>
}


// WITH_ORDER
// EXIST: {"lookupString": "SomeClassWithConstructor", "tailText": " (<root>)" }
// EXIST: {"lookupString": "SomeClassWithConstructor", "tailText": "(a: Int) (<root>)" }
// NOTHING_ELSE