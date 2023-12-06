// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
fun String.foo(p: (String, Char) -> Unit){}

fun test() {
    "".fo<caret>
}

// IGNORE_K2
// EXIST: { lookupString:"foo", itemText: "foo", tailText: "(p: (String, Char) -> Unit) for String in <root>", typeText:"Unit", icon: "Function"}
// EXIST: { lookupString:"foo", itemText: "foo", tailText: " { String, Char -> ... } (p: (String, Char) -> Unit) for String in <root>", typeText:"Unit", icon: "Function"}
