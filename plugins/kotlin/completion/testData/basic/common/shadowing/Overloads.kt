// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package ppp

class C {
    fun xxx(p: Int) = ""

    fun foo() {
        xx<caret>
    }
}

fun C.xxx(p: Int) = 1
fun Any.xxx(c: Char) = 1
fun C.xxx(c: Char) = 1

// EXIST: { lookupString: "xxx", itemText: "xxx", tailText: "(p: Int)", typeText: "String", icon: "Method"}
// EXIST: { lookupString: "xxx", itemText: "xxx", tailText: "(c: Char) for C in ppp", typeText: "Int", icon: "Function"}
// NOTHING_ELSE
