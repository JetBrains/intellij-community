// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
fun foo(p: (String, Char) -> Unit){}

fun test() {
    foo<caret>
}

// EXIST: { lookupString:"foo", itemText: "foo", tailText: "(p: (String, Char) -> Unit) (<root>)", typeText:"Unit", icon: "Function"}
// EXIST: { lookupString:"foo", itemText: "foo", tailText: " { String, Char -> ... } (p: (String, Char) -> Unit) (<root>)", typeText:"Unit", icon: "Function"}
