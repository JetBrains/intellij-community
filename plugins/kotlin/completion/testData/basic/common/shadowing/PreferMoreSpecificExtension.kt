// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package ppp

interface I

class C : I {
    fun foo() {
        xx<caret>
    }
}

val Any.xxx: Int get() = 1
val I.xxx: Int get() = 1

fun Any.xxx() = 1
fun C.xxx() = 1

// EXIST: { lookupString: "xxx", itemText: "xxx", tailText: " for I in ppp", typeText: "Int", icon: "org/jetbrains/kotlin/idea/icons/field_value.svg"}
// EXIST: { lookupString: "xxx", itemText: "xxx", tailText: "() for C in ppp", typeText: "Int", icon: "Function"}
// NOTHING_ELSE
