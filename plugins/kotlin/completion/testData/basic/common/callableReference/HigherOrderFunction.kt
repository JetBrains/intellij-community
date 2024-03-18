// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
fun xfoo(p: (String, Char) -> Unit){}

fun test() {
    ::xfo<caret>
}

// EXIST: { lookupString:"xfoo", itemText: "xfoo", tailText: "(p: (String, Char) -> Unit) (<root>)", typeText:"Unit", icon: "Function"}
// NOTHING_ELSE
